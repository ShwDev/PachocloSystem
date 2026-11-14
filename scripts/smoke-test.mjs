#!/usr/bin/env node
/**
 * Smoke test de extremo a extremo contra el stack de Docker Compose LOCAL.
 *
 * Recorre, para Medicamentos y Trabajadores (borrado físico):
 *   POST -> GET -> PUT -> GET -> reinicio de contenedores (down sin -v + up) -> GET -> DELETE -> GET (404)
 * y comprueba en cada paso el conteo del documento directamente en MongoDB con mongosh.
 *
 * Para Pacientes (baja lógica) comprueba que, tras el DELETE, el paciente deja de salir en las
 * consultas (GET por id = 404 y no aparece en el listado) pero su documento sigue en MongoDB con
 * activo=false.
 *
 * Requisitos: Node >= 22, Docker con Compose v2 y el stack levantado con el override de demo
 * (publica la API en el 8080):
 *
 *   docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
 *   node scripts/smoke-test.mjs
 *
 * Opciones (variables de entorno):
 *   SMOKE_API_URL        URL de PachocloSystem          (por defecto http://localhost:8080)
 *   SMOKE_ENV_FILE       .env que usa el compose        (por defecto .env si existe, si no .env.example)
 *   SMOKE_COMPOSE_FILES  archivos compose, separados por coma
 *                        (por defecto docker-compose.yml,docker-compose.demo.yml)
 *   SMOKE_SIN_REINICIO=1 se salta el reinicio de contenedores
 *
 * Solo se ejecuta contra localhost: nunca escribe en un despliegue remoto.
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');
const API = (process.env.SMOKE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const ENV_FILE =
  process.env.SMOKE_ENV_FILE ?? (existsSync(join(RAIZ, '.env')) ? '.env' : '.env.example');
const COMPOSE_FILES = (process.env.SMOKE_COMPOSE_FILES ?? 'docker-compose.yml,docker-compose.demo.yml')
  .split(',')
  .map((f) => f.trim())
  .filter(Boolean);
const SIN_REINICIO = process.env.SMOKE_SIN_REINICIO === '1';

// ---------------------------------------------------------------- utilidades

const hostApi = new URL(API).hostname;
if (!['localhost', '127.0.0.1', '::1', '[::1]'].includes(hostApi)) {
  console.error(`Rechazado: ${API} no es local. Este script solo se ejecuta contra localhost.`);
  process.exit(2);
}

let pasos = 0;
function ok(mensaje) {
  pasos++;
  console.log(`  ✓ ${mensaje}`);
}
function fallo(mensaje) {
  throw new Error(mensaje);
}
function esperar(condicion, mensaje) {
  if (!condicion) fallo(mensaje);
}

function leerEnv(archivo) {
  const valores = {};
  const ruta = join(RAIZ, archivo);
  if (!existsSync(ruta)) fallo(`No existe ${archivo}`);
  for (const linea of readFileSync(ruta, 'utf8').split(/\r?\n/)) {
    const m = linea.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m) valores[m[1]] = m[2].replace(/^['"]|['"]$/g, '');
  }
  return valores;
}

function compose(...args) {
  const base = ['compose', '--env-file', ENV_FILE];
  for (const f of COMPOSE_FILES) base.push('-f', f);
  return execFileSync('docker', [...base, ...args], {
    cwd: RAIZ,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

/** Evalúa una expresión en mongosh dentro del contenedor mongo y devuelve su salida (JSON). */
function mongo(base, expresion) {
  const salida = compose('exec', '-T', 'mongo', 'mongosh', '--quiet', base, '--eval',
    `print(EJSON.stringify(${expresion}))`);
  return JSON.parse(salida.trim().split(/\r?\n/).pop());
}
const contar = (base, coleccion, filtro) =>
  mongo(base, `db.getCollection(${JSON.stringify(coleccion)}).countDocuments(${JSON.stringify(filtro)})`);
const documento = (base, coleccion, id) =>
  mongo(base, `db.getCollection(${JSON.stringify(coleccion)}).findOne({_id: ${JSON.stringify(id)}})`);

let token = null;
async function http(metodo, ruta, cuerpo) {
  const respuesta = await fetch(API + ruta, {
    method: metodo,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(cuerpo !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
  });
  const texto = await respuesta.text();
  let json = null;
  try {
    json = texto ? JSON.parse(texto) : null;
  } catch {
    json = texto;
  }
  return { status: respuesta.status, json };
}
async function exigir(metodo, ruta, cuerpo, estado) {
  const r = await http(metodo, ruta, cuerpo);
  if (r.status !== estado) {
    fallo(`${metodo} ${ruta}: se esperaba ${estado} y llegó ${r.status} ${JSON.stringify(r.json)}`);
  }
  return r.json;
}

async function esperarListo(url, segundos = 180) {
  const limite = Date.now() + segundos * 1000;
  while (Date.now() < limite) {
    try {
      const r = await fetch(url);
      if (r.ok) return;
    } catch {
      /* todavía arrancando */
    }
    await new Promise((resolver) => setTimeout(resolver, 2000));
  }
  fallo(`${url} no respondió 200 en ${segundos} s`);
}

async function login() {
  const env = leerEnv(ENV_FILE);
  token = null;
  const r = await exigir('POST', '/api/auth/login', {
    username: env.ADMIN_USERNAME || 'admin',
    password: env.ADMIN_PASSWORD,
  }, 200);
  esperar(!r.debeCambiarPassword,
    'El admin tiene la contraseña pendiente de cambio; cámbiela antes de correr el smoke test.');
  token = r.token;
}

// ---------------------------------------------------------------- recorrido

const sufijo = Date.now().toString(36).toUpperCase();
// Los nombres de personas solo admiten letras y espacios: el mismo sufijo, pasado a letras.
const sufijoLetras = [...String(Date.now()).slice(-6)].map((d) => 'ABCDEFGHIJ'[d]).join('');
const estado = {};

async function crear() {
  console.log('\n[1] POST: crear un registro de cada recurso');
  const med = await exigir('POST', '/api/medicamentos', {
    nombre: 'Smoke Dolex', principioActivo: 'Paracetamol', presentacion: 'TABLETA',
    concentracion: '500 mg', laboratorio: 'GSK', lote: `SMK-${sufijo}`, cantidadStock: 20,
    stockMinimo: 5, fechaVencimiento: '2030-01-31', ubicacion: 'Estante S1',
  }, 201);
  estado.med = med.idMedicamento;
  esperar(contar('medicamentos', 'medicamentos', { _id: estado.med }) === 1, 'El medicamento no está en Mongo');
  ok(`medicamento ${estado.med} creado (201) y presente en medicamentos.medicamentos (1 documento)`);

  const trab = await exigir('POST', '/api/trabajadores', {
    nombre: `Smoke Doctor ${sufijoLetras}`, rol: 'Doctor', especialidad: 'Cardiologia',
  }, 201);
  estado.trab = trab.idTrabajador;
  esperar(contar('pachoclosystem', 'trabajadores', { _id: estado.trab }) === 1, 'El trabajador no está en Mongo');
  ok(`trabajador ${estado.trab} creado (201) y presente en pachoclosystem.trabajadores (1 documento)`);

  const pac = await exigir('POST', '/api/pacientes', {
    nombre: `Smoke Paciente ${sufijoLetras}`, edad: 40, habitacion: 101,
  }, 201);
  estado.pac = pac.idPaciente;
  esperar(contar('pachoclosystem', 'pacientes', { _id: estado.pac, activo: true }) === 1,
    'El paciente no está activo en Mongo');
  ok(`paciente ${estado.pac} creado (201) y presente en pachoclosystem.pacientes con activo=true`);
}

async function consultar(etapa) {
  const med = await exigir('GET', `/api/medicamentos/${estado.med}`, undefined, 200);
  const trab = await exigir('GET', `/api/trabajadores/${estado.trab}`, undefined, 200);
  const pac = await exigir('GET', `/api/pacientes/${estado.pac}`, undefined, 200);
  const lista = await exigir('GET', '/api/medicamentos', undefined, 200);
  esperar(lista.some((m) => m.idMedicamento === estado.med), 'El medicamento no sale en el listado');
  ok(`GET (${etapa}): los tres registros se consultan por id y el medicamento sale en el listado`);
  return { med, trab, pac };
}

async function actualizar() {
  console.log('\n[3] PUT/PATCH: modificar los registros');
  await exigir('PUT', `/api/medicamentos/${estado.med}`, {
    nombre: 'Smoke Dolex', principioActivo: 'Paracetamol', presentacion: 'TABLETA',
    concentracion: '500 mg', laboratorio: 'GSK', lote: `SMK-${sufijo}`, stockMinimo: 8,
    fechaVencimiento: '2030-06-30', ubicacion: 'Estante S2',
  }, 200);
  ok('PUT /api/medicamentos/{id}: ubicación Estante S2, stock mínimo 8');
  await exigir('PUT', `/api/trabajadores/${estado.trab}`, {
    nombre: `Smoke Doctor ${sufijoLetras} Ruiz`, rol: 'Doctor', especialidad: 'Neurologia',
  }, 200);
  ok('PUT /api/trabajadores/{id}: nombre y especialidad Neurologia');
  await exigir('PATCH', `/api/pacientes/${estado.pac}/habitacion`, { habitacion: 305 }, 200);
  ok('PATCH /api/pacientes/{id}/habitacion: habitación 305');
}

function verificarCambios({ med, trab, pac }, etapa) {
  esperar(med.ubicacion === 'Estante S2' && med.stockMinimo === 8, `Medicamento sin los cambios (${etapa})`);
  esperar(trab.especialidad === 'Neurologia' && trab.nombreCompleto.endsWith('Ruiz'),
    `Trabajador sin los cambios (${etapa})`);
  esperar(pac.habitacion === 305, `Paciente sin el cambio de habitación (${etapa})`);
  const enMongo = documento('medicamentos', 'medicamentos', estado.med);
  esperar(JSON.stringify(enMongo).includes('Estante S2'), `Mongo no tiene la ubicación nueva (${etapa})`);
  esperar(documento('pachoclosystem', 'trabajadores', estado.trab).especialidad === 'Neurologia',
    `Mongo no tiene la especialidad nueva (${etapa})`);
  esperar(documento('pachoclosystem', 'pacientes', estado.pac).habitacion === 305,
    `Mongo no tiene la habitación nueva (${etapa})`);
  ok(`cambios visibles por la API y guardados en MongoDB (${etapa})`);
}

async function reiniciar() {
  console.log('\n[5] Reinicio: docker compose down (SIN -v) + up -d');
  compose('down');
  ok('contenedores eliminados; el volumen mongo-datos se conserva');
  compose('up', '-d');
  await esperarListo(`${API}/actuator/health/readiness`);
  ok('stack levantado de nuevo y PachocloSystem listo (readiness 200)');
  await login();
}

async function eliminar() {
  console.log('\n[7] DELETE con borrado físico: Medicamentos y Trabajadores');
  await exigir('DELETE', `/api/medicamentos/${estado.med}`, undefined, 204);
  await exigir('GET', `/api/medicamentos/${estado.med}`, undefined, 404);
  esperar(contar('medicamentos', 'medicamentos', { _id: estado.med }) === 0, 'El medicamento sigue en Mongo');
  ok(`medicamento ${estado.med}: DELETE 204, GET 404 y 0 documentos en Mongo`);

  await exigir('DELETE', `/api/trabajadores/${estado.trab}`, undefined, 204);
  await exigir('GET', `/api/trabajadores/${estado.trab}`, undefined, 404);
  esperar(contar('pachoclosystem', 'trabajadores', { _id: estado.trab }) === 0, 'El trabajador sigue en Mongo');
  ok(`trabajador ${estado.trab}: DELETE 204, GET 404 y 0 documentos en Mongo`);

  console.log('\n[8] DELETE con baja lógica: Pacientes');
  await exigir('DELETE', `/api/pacientes/${estado.pac}`, undefined, 204);
  await exigir('GET', `/api/pacientes/${estado.pac}`, undefined, 404);
  const lista = await exigir('GET', '/api/pacientes', undefined, 200);
  esperar(!lista.some((p) => p.idPaciente === estado.pac), 'El paciente sigue saliendo en el listado');
  ok(`paciente ${estado.pac}: DELETE 204, GET 404 y ya no sale en GET /api/pacientes`);
  const doc = documento('pachoclosystem', 'pacientes', estado.pac);
  esperar(doc !== null, 'El documento del paciente desapareció de Mongo (debía conservarse)');
  esperar(doc.activo === false, `El documento del paciente no tiene activo=false: ${JSON.stringify(doc)}`);
  esperar(doc.habitacion === 305, 'El documento del paciente perdió sus datos');
  ok('el documento del paciente sigue en MongoDB con activo=false y sus datos intactos');
}

async function main() {
  console.log(`Smoke test contra ${API}`);
  console.log(`Compose: ${COMPOSE_FILES.join(' + ')} (env: ${ENV_FILE})`);

  console.log('\n[0] Comprobaciones previas');
  await esperarListo(`${API}/actuator/health/readiness`, 30);
  ok('PachocloSystem responde (readiness 200)');
  await login();
  ok('login del administrador');
  const indices = mongo('medicamentos', 'db.medicamentos.getIndexes().map(i => i.name)');
  esperar(indices.some((n) => n.includes('claveUnica')), `Falta el índice único de medicamentos: ${indices}`);
  const indicesUsuarios = mongo('pachoclosystem', 'db.usuarios.getIndexes().map(i => i.name)');
  esperar(indicesUsuarios.some((n) => n.includes('username')), `Falta el índice de username: ${indicesUsuarios}`);
  ok('índices creados al arrancar (medicamentos.claveUnica, usuarios.username)');

  await crear();
  console.log('\n[2] GET: consultar lo creado');
  await consultar('tras crear');
  await actualizar();
  console.log('\n[4] GET: los cambios se ven');
  verificarCambios(await consultar('tras actualizar'), 'tras actualizar');
  if (SIN_REINICIO) {
    console.log('\n[5] Reinicio omitido (SMOKE_SIN_REINICIO=1)');
  } else {
    await reiniciar();
    console.log('\n[6] GET: los datos sobreviven al reinicio');
    verificarCambios(await consultar('tras reiniciar'), 'tras reiniciar');
  }
  await eliminar();

  console.log(`\nOK: ${pasos} comprobaciones superadas.`);
}

main().catch((error) => {
  console.error(`\n✗ FALLO: ${error.message}`);
  if (error.stderr) console.error(String(error.stderr));
  process.exit(1);
});
