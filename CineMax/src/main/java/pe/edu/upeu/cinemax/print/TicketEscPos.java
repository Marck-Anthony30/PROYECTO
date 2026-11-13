package pe.edu.upeu.cinemax.print;

import pe.edu.upeu.cinemax.model.VentaDetalle;
import pe.edu.upeu.cinemax.util.Formato;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;

/**
 * Convierte un {@link TicketVenta} en:
 * <ul>
 *   <li>texto de <b>vista previa</b> de 32 columnas, igual que el papel de 58 mm; y</li>
 *   <li>los <b>bytes ESC/POS</b> que se envían a la impresora térmica.</li>
 * </ul>
 *
 * <p>Una POS de 58 mm imprime 32 caracteres por línea con la fuente normal.
 * Por eso todo el texto se alinea a 32 columnas y se quitan las tildes/ñ,
 * porque muchas impresoras económicas no las interpretan bien.</p>
 */
public final class TicketEscPos {

    /** Columnas que caben en una POS de 58 mm (fuente A). */
    public static final int ANCHO = 32;

    /**
     * Si tu impresora NO tiene cortador automático y al final imprime caracteres
     * extraños, cambia este valor a {@code false}.
     */
    private static final boolean CORTAR_PAPEL = true;

    private TicketEscPos() {
    }

    /**
     * Devuelve el ticket como texto para mostrarlo en pantalla antes de imprimir.
     * Se usa en el diálogo de impresión como "vista previa".
     */
    public static String vistaPrevia(TicketVenta t) {
        StringBuilder b = new StringBuilder(1024);
        b.append(centrar(TicketVenta.RAZON_SOCIAL)).append('\n');
        b.append(centrar("RUC: " + TicketVenta.RUC)).append('\n');
        b.append(centrar(TicketVenta.DIRECCION)).append('\n');
        b.append(centrar("Tel: " + TicketVenta.TELEFONO)).append('\n');
        b.append(separador()).append('\n');
        b.append(centrar(texto(t.getTipoComprobante()))).append('\n');
        b.append(centrar(texto(t.getSerie()) + " - " + texto(t.getNumero()))).append('\n');
        b.append("Fecha: ").append(texto(t.getFecha())).append(' ')
                .append(texto(t.getHora())).append('\n');
        b.append("Cliente: ").append(texto(t.getClienteNombre())).append('\n');
        b.append("DNI: ").append(texto(t.getClienteDni())).append('\n');
        b.append("Cajero: ").append(texto(t.getCajero())).append('\n');
        b.append(separador()).append('\n');
        b.append("CANT. DESCRIPCION").append('\n');
        b.append(separador()).append('\n');

        for (VentaDetalle d : t.getDetalles()) {
            String prefijo = d.getCantidad() + " x ";
            b.append(prefijo)
                    .append(recortar(d.getNombreProducto(), ANCHO - prefijo.length())).append('\n');
            if (d.getDescripcionFuncion() != null && !d.getDescripcionFuncion().isBlank()) {
                for (String parte : envolver(d.getDescripcionFuncion(), ANCHO - 4)) {
                    b.append("    ").append(parte).append('\n');
                }
            }
            if (d.getAsientos() != null && !d.getAsientos().isBlank()) {
                b.append("    Asientos: ").append(recortar(d.getAsientos(), ANCHO - 14)).append('\n');
            }
            b.append("    P.U. ").append(Formato.dinero(d.getPrecioUnit())).append('\n');
            b.append("    TOTAL ").append(Formato.dinero(d.getNetoTotal())).append('\n');
        }

        b.append(separador()).append('\n');
        b.append(dosColumnas("Neto:", Formato.dinero(t.getNeto()))).append('\n');
        b.append(dosColumnas("IGV (18%):", Formato.dinero(t.getIgv()))).append('\n');
        b.append(dosColumnas("TOTAL:", Formato.dinero(t.getTotal()))).append('\n');
        b.append(separador()).append('\n');
        b.append(centrar("[ CODIGO QR ]")).append('\n');
        for (String parte : envolver(t.textoQr(), ANCHO)) {
            b.append(parte).append('\n');
        }
        b.append(centrar("Gracias por su compra")).append('\n');
        b.append(centrar("Representacion impresa de la")).append('\n');
        b.append(centrar(texto(t.getTipoComprobante()))).append('\n');
        if (t.isPrueba()) {
            b.append(centrar("*** DOCUMENTO DE PRUEBA ***")).append('\n');
        }
        return ascii(b.toString());
    }

    /**
     * Genera los bytes ESC/POS que entiende la impresora térmica:
     * inicializa, imprime el texto y dibuja el código QR con el comando nativo
     * {@code GS ( k} (no hace falta ninguna librería externa).
     */
    public static byte[] generar(TicketVenta t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(2048);

        // 1) ESC @ -> reinicia la impresora (borra negritas y tamaños anteriores).
        escribir(out, 0x1B, 0x40);

        // 2) Encabezado centrado y con el nombre en doble altura.
        escribir(out, 0x1B, 0x61, 0x01);            // centrar
        escribir(out, 0x1B, 0x21, 0x10);            // doble altura
        escribirTexto(out, TicketVenta.RAZON_SOCIAL + "\n");
        escribir(out, 0x1B, 0x21, 0x00);            // tamaño normal
        escribirTexto(out, "RUC: " + TicketVenta.RUC + "\n");
        escribirTexto(out, TicketVenta.DIRECCION + "\n");
        escribirTexto(out, "Tel: " + TicketVenta.TELEFONO + "\n");
        escribirTexto(out, separador() + "\n");
        escribirTexto(out, texto(t.getTipoComprobante()) + "\n");
        escribirTexto(out, t.getSerie() + " - " + t.getNumero() + "\n");

        // 3) Datos de la venta alineados a la izquierda.
        escribir(out, 0x1B, 0x61, 0x00);            // izquierda
        escribirTexto(out, "Fecha: " + t.getFecha() + " " + t.getHora() + "\n");
        escribirTexto(out, "Cliente: " + t.getClienteNombre() + "\n");
        escribirTexto(out, "DNI: " + t.getClienteDni() + "\n");
        escribirTexto(out, "Cajero: " + t.getCajero() + "\n");
        escribirTexto(out, separador() + "\n");
        escribirTexto(out, "CANT. DESCRIPCION\n");
        escribirTexto(out, separador() + "\n");

        // 4) Detalle: una línea con cantidad y nombre, y debajo precio e importe.
        for (VentaDetalle d : t.getDetalles()) {
            String prefijo = d.getCantidad() + " x ";
            escribirTexto(out, prefijo
                    + recortar(d.getNombreProducto(), ANCHO - prefijo.length()) + "\n");
            if (d.getDescripcionFuncion() != null && !d.getDescripcionFuncion().isBlank()) {
                for (String parte : envolver(d.getDescripcionFuncion(), ANCHO - 4)) {
                    escribirTexto(out, "    " + parte + "\n");
                }
            }
            if (d.getAsientos() != null && !d.getAsientos().isBlank()) {
                escribirTexto(out, "    Asientos: " + recortar(d.getAsientos(), ANCHO - 14) + "\n");
            }
            escribirTexto(out, "    P.U. " + Formato.dinero(d.getPrecioUnit()) + "\n");
            escribirTexto(out, "    TOTAL " + Formato.dinero(d.getNetoTotal()) + "\n");
        }

        // 5) Totales (el total va en negrita).
        escribirTexto(out, separador() + "\n");
        escribirTexto(out, dosColumnas("Neto:", Formato.dinero(t.getNeto())) + "\n");
        escribirTexto(out, dosColumnas("IGV (18%):", Formato.dinero(t.getIgv())) + "\n");
        escribir(out, 0x1B, 0x45, 0x01);            // negrita activada
        escribirTexto(out, dosColumnas("TOTAL:", Formato.dinero(t.getTotal())) + "\n");
        escribir(out, 0x1B, 0x45, 0x00);            // negrita desactivada
        escribirTexto(out, separador() + "\n");

        // 6) Código QR centrado con los datos "tipo SUNAT".
        escribir(out, 0x1B, 0x61, 0x01);            // centrar
        out.writeBytes(qr(t.textoQr()));
        escribirTexto(out, "\n");
        escribirTexto(out, "Gracias por su compra\n");
        escribirTexto(out, "Representacion impresa de la\n");
        escribirTexto(out, texto(t.getTipoComprobante()) + "\n");
        if (t.isPrueba()) {
            escribirTexto(out, "*** DOCUMENTO DE PRUEBA ***\n");
        }
        escribir(out, 0x1B, 0x61, 0x00);            // volver a la izquierda

        // 7) Avanza el papel y, si la impresora tiene cortador, corta el ticket.
        escribir(out, 0x1B, 0x64, 0x04);            // ESC d 4 : avanza 4 líneas
        if (CORTAR_PAPEL) {
            escribir(out, 0x1D, 0x56, 0x42, 0x00);  // GS V B 0 : cortar
        }
        return out.toByteArray();
    }

    // ============================================================
    //  CÓDIGO QR NATIVO (comando ESC/POS "GS ( k")
    // ============================================================
    private static byte[] qr(String datos) {
        byte[] contenido = ascii(datos).getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);

        // Modelo 2 (el estándar de los lectores de celular).
        escribir(out, 0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00);
        // Tamaño del módulo: 6 puntos (buen contraste en 58 mm).
        escribir(out, 0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x06);
        // Nivel de corrección de errores: M (tolera ~15 % de daño).
        escribir(out, 0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31);

        // Guardar el contenido. El largo se envía en dos bytes: pL (bajo) y pH (alto).
        int largo = contenido.length + 3;
        escribir(out, 0x1D, 0x28, 0x6B, largo & 0xFF, (largo >> 8) & 0xFF, 0x31, 0x50, 0x30);
        out.writeBytes(contenido);

        // Imprimir el QR ya guardado.
        escribir(out, 0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30);
        return out.toByteArray();
    }

    // ============================================================
    //  Utilidades de formato
    // ============================================================
    private static void escribir(ByteArrayOutputStream out, int... bytes) {
        for (int b : bytes) {
            out.write(b);
        }
    }

    private static void escribirTexto(ByteArrayOutputStream out, String texto) {
        out.writeBytes(ascii(texto).getBytes(StandardCharsets.US_ASCII));
    }

    private static String separador() {
        return "-".repeat(ANCHO);
    }

    private static String centrar(String texto) {
        String limpio = ascii(texto);
        if (limpio.length() >= ANCHO) return limpio;
        int espacios = (ANCHO - limpio.length()) / 2;
        return " ".repeat(espacios) + limpio;
    }

    private static String dosColumnas(String izquierda, String derecha) {
        int espacios = ANCHO - izquierda.length() - derecha.length();
        if (espacios < 1) espacios = 1;
        return izquierda + " ".repeat(espacios) + derecha;
    }

    private static String recortar(String texto, int maximo) {
        String limpio = ascii(texto);
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }

    private static List<String> envolver(String texto, int ancho) {
        java.util.ArrayList<String> lineas = new java.util.ArrayList<>();
        String resto = ascii(texto).trim();
        // Corta preferentemente en un espacio para no partir palabras o la hora.
        while (resto.length() > ancho) {
            int corte = resto.lastIndexOf(' ', ancho);
            if (corte <= 0) corte = ancho;
            lineas.add(resto.substring(0, corte).trim());
            resto = resto.substring(corte).trim();
        }
        lineas.add(resto);
        return lineas;
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    /**
     * Quita tildes, ñ y otros caracteres que las impresoras térmicas económicas
     * no imprimen bien. Ejemplo: "Película" → "Pelicula".
     */
    private static String ascii(String valor) {
        if (valor == null) return "";
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String reemplazado = sinAcentos
                .replace('ñ', 'n').replace('Ñ', 'N')
                .replace('°', ' ').replace('“', '"').replace('”', '"')
                .replace('·', '-').replace('•', '-')
                .replace('–', '-').replace('—', '-');
        // Red de seguridad: cualquier carácter fuera del ASCII imprimible se
        // convierte en "?" para que nunca llegue basura a la impresora.
        return reemplazado.replaceAll("[^\\x20-\\x7E\\n\\r\\t]", "?");
    }
}
