package com.pachoclosystem.pachoclosystem.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * IP del cliente para el límite de intentos de login.
 *
 * <p>Por defecto es {@code getRemoteAddr()}, la del socket, y
 * {@code X-Forwarded-For} se ignora: cualquiera puede escribir esa cabecera.
 * Solo si la petición llega de un proxy de confianza
 * ({@code app.login.proxies-confiables}: IPs exactas o rangos CIDR, como
 * {@code 100.64.0.0/10}) se lee {@code X-Forwarded-For} de derecha a
 * izquierda saltando los proxies de confianza: la primera IP que no es de
 * confianza es la que vio el último proxy propio, y es la del cliente. Lo que
 * está más a la izquierda lo pudo escribir el propio cliente y no se usa.</p>
 *
 * <p>Los rangos sirven para plataformas cuyos proxies no tienen una IP fija.
 * Si esa entrada no tiene forma de IP, o la cabecera falta o solo contiene
 * proxies de confianza, se usa {@code getRemoteAddr()}. Las IPs se comparan por
 * su valor ({@code ::1} y {@code 0:0:0:0:0:0:0:1} son la misma) y nunca se
 * resuelven nombres por DNS.</p>
 */
@Component
public class ResolutorIpCliente {

    static final String CABECERA = "X-Forwarded-For";

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F.]*:[0-9a-fA-F:.]*$");

    /** Red y longitud de prefijo; una IP exacta es un rango /32 (IPv4) o /128 (IPv6). */
    private record Rango(byte[] red, int prefijo) {

        boolean contiene(InetAddress ip) {
            byte[] direccion = ip.getAddress();
            if (direccion.length != red.length) {
                return false;
            }
            int bytesCompletos = prefijo / 8;
            for (int i = 0; i < bytesCompletos; i++) {
                if (direccion[i] != red[i]) {
                    return false;
                }
            }
            int bitsRestantes = prefijo % 8;
            if (bitsRestantes == 0) {
                return true;
            }
            int mascara = (0xFF << (8 - bitsRestantes)) & 0xFF;
            return (direccion[bytesCompletos] & mascara) == (red[bytesCompletos] & mascara);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Rango otro && prefijo == otro.prefijo && Arrays.equals(red, otro.red);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(red), prefijo);
        }

        @Override
        public String toString() {
            return "Rango[red=" + Arrays.toString(red) + ", prefijo=" + prefijo + "]";
        }
    }

    private final List<Rango> proxiesConfiables;

    public ResolutorIpCliente(@Value("${app.login.proxies-confiables:}") String configuracion) {
        List<Rango> rangos = new ArrayList<>();
        if (configuracion != null) {
            for (String parte : configuracion.split(",")) {
                String entrada = parte.trim();
                if (entrada.isEmpty()) {
                    continue;
                }
                rangos.add(rango(entrada));
            }
        }
        this.proxiesConfiables = List.copyOf(rangos);
    }

    /** IP del cliente de la petición (ver la descripción de la clase). */
    public String ipDe(HttpServletRequest peticion) {
        String remota = peticion.getRemoteAddr();
        if (remota == null || !esConfiable(remota)) {
            return remota;
        }
        List<String> saltos = new ArrayList<>();
        var cabeceras = peticion.getHeaders(CABECERA);
        while (cabeceras != null && cabeceras.hasMoreElements()) {
            for (String salto : cabeceras.nextElement().split(",")) {
                saltos.add(salto.trim());
            }
        }
        for (int i = saltos.size() - 1; i >= 0; i--) {
            String salto = saltos.get(i);
            if (esConfiable(salto)) {
                continue;
            }
            return esIp(salto) ? salto : remota;
        }
        return remota;
    }

    private boolean esConfiable(String valor) {
        InetAddress ip = literal(valor);
        return ip != null && proxiesConfiables.stream().anyMatch(rango -> rango.contiene(ip));
    }

    /** Una entrada de la configuración: IP literal o IP/prefijo. Si no es válida, impide arrancar. */
    private static Rango rango(String entrada) {
        int barra = entrada.indexOf('/');
        String direccion = barra < 0 ? entrada : entrada.substring(0, barra);
        InetAddress ip = literal(direccion);
        if (ip == null) {
            throw entradaInvalida(entrada);
        }
        int bits = ip.getAddress().length * 8;
        int prefijo = bits;
        if (barra >= 0) {
            String textoPrefijo = entrada.substring(barra + 1);
            if (!textoPrefijo.matches("\\d{1,3}")) {
                throw entradaInvalida(entrada);
            }
            prefijo = Integer.parseInt(textoPrefijo);
            if (prefijo > bits) {
                throw entradaInvalida(entrada);
            }
        }
        return new Rango(ip.getAddress(), prefijo);
    }

    private static IllegalStateException entradaInvalida(String entrada) {
        return new IllegalStateException("app.login.proxies-confiables "
                + "(APP_LOGIN_PROXIES_CONFIABLES) solo admite IPs literales o rangos CIDR (p. ej. "
                + "100.64.0.0/10) separados por comas; no es válido: '" + sanear(entrada) + "'.");
    }

    /** La IP de un texto con forma de IP literal; si no lo es, null. Nunca consulta DNS. */
    private static InetAddress literal(String valor) {
        if (valor == null || !esIp(valor)) {
            return null;
        }
        try {
            return InetAddress.ofLiteral(valor);
        } catch (IllegalArgumentException noEsUnaIp) {
            return null;
        }
    }

    private static boolean esIp(String valor) {
        return valor.length() <= 45 && (IPV4.matcher(valor).matches() || IPV6.matcher(valor).matches());
    }

    /** Sin caracteres de control, para que el mensaje de arranque no inyecte líneas. */
    private static String sanear(String valor) {
        StringBuilder limpio = new StringBuilder(valor.length());
        for (char caracter : valor.toCharArray()) {
            limpio.append(Character.isISOControl(caracter) ? '?' : caracter);
        }
        return limpio.toString();
    }
}
