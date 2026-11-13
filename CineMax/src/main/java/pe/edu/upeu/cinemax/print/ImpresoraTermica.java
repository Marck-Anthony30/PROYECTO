package pe.edu.upeu.cinemax.print;

import com.fazecast.jSerialComm.SerialPort;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Envía la boleta a una impresora POS de 58 mm por dos caminos:
 *
 * <ul>
 *   <li><b>USB:</b> usa la impresora instalada en Windows y {@code javax.print},
 *       que ya viene con Java.</li>
 *   <li><b>Bluetooth:</b> usa la librería <b>jSerialComm</b> para abrir el puerto
 *       COM que crea Windows al emparejar la impresora, escribir los bytes y
 *       esperar a que se envíen.</li>
 * </ul>
 *
 * <p>Con jSerialComm el envío Bluetooth es confiable: abre el puerto a 9600
 * baudios, escribe el ticket completo y lo cierra después de unos milisegundos.</p>
 */
public final class ImpresoraTermica {

    /** Impresora instalada en Windows (camino USB). */
    public record Impresora(String nombre, PrintService servicio) {
        @Override
        public String toString() {
            return nombre;
        }
    }

    /** Puerto serie/COM (camino Bluetooth). */
    public record Puerto(String nombre, String descripcion) {
        @Override
        public String toString() {
            if (descripcion == null || descripcion.isBlank()) return nombre;
            return descripcion.contains(nombre) ? descripcion : nombre + " - " + descripcion;
        }
    }

    private ImpresoraTermica() {
    }

    // ============================================================
    //  USB: impresoras instaladas en Windows
    // ============================================================

    /** Lista las impresoras de Windows, poniendo primero las POS de 58 mm. */
    public static List<Impresora> impresoras() {
        List<Impresora> lista = new ArrayList<>();
        try {
            for (PrintService servicio : PrintServiceLookup.lookupPrintServices(null, null)) {
                lista.add(new Impresora(servicio.getName(), servicio));
            }
        } catch (Exception ignored) {
            // Windows no devolvió impresoras: la lista queda vacía.
        }
        lista.sort((a, b) -> Integer.compare(puntaje(b.nombre()), puntaje(a.nombre())));
        return lista;
    }

    /** Indica si el nombre parece de una POS térmica. */
    public static boolean esPos58(String nombre) {
        return puntaje(nombre) > 0;
    }

    private static int puntaje(String nombre) {
        String n = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
        int puntos = 0;
        if (n.contains("58")) puntos += 4;
        if (n.contains("pos")) puntos += 2;
        if (n.contains("thermal") || n.contains("termic")) puntos += 1;
        return puntos;
    }

    /** Imprime por USB en la impresora elegida. */
    public static void imprimirUsb(Impresora impresora, byte[] datos) {
        if (impresora == null) {
            throw new IllegalArgumentException("Selecciona una impresora USB.");
        }
        try {
            DocPrintJob trabajo = impresora.servicio().createPrintJob();
            // BYTE_ARRAY.AUTOSENSE envía los bytes tal cual, sin convertirlos.
            Doc documento = new SimpleDoc(datos, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
            trabajo.print(documento, null);
        } catch (PrintException e) {
            throw new RuntimeException("No se pudo imprimir en \"" + impresora.nombre()
                    + "\": " + e.getMessage(), e);
        }
    }

    // ============================================================
    //  Bluetooth: puerto serie (COM)
    // ============================================================

    /**
     * Lista los puertos COM que existen en el equipo. Los de Bluetooth suelen
     * llamarse "Standard Serial over Bluetooth link (COM5)".
     */
    public static List<Puerto> puertosCom() {
        List<Puerto> lista = new ArrayList<>();
        try {
            for (SerialPort puerto : SerialPort.getCommPorts()) {
                lista.add(new Puerto(puerto.getSystemPortName(), puerto.getDescriptivePortName()));
            }
        } catch (Throwable ignored) {
            // Sin permisos o sin puertos: lista vacía.
        }
        return lista;
    }

    /**
     * Abre y cierra el puerto para comprobar que responde. No imprime nada.
     * Sirve para saber cuál de los COM es el de la impresora.
     */
    public static void probarBluetooth(String puerto, int baudios) {
        if (puerto == null || puerto.isBlank()) {
            throw new IllegalArgumentException("Selecciona el puerto COM de la impresora Bluetooth.");
        }
        SerialPort serial = SerialPort.getCommPort(puerto);
        serial.setComPortParameters(baudios, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serial.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        if (!serial.openPort()) {
            throw new RuntimeException("No se pudo abrir el puerto " + puerto + ".\n"
                    + "Otra aplicación puede estar usándolo o la impresora está apagada.");
        }
        serial.closePort();
    }

    /**
     * Imprime por Bluetooth/SAP escribiendo los bytes en el puerto COM.
     *
     * @param puerto   nombre del puerto, por ejemplo "COM5"
     * @param baudios  velocidad (9600 es lo habitual en POS Bluetooth)
     * @param datos    bytes ESC/POS del ticket
     */
    public static void imprimirBluetooth(String puerto, int baudios, byte[] datos) {
        if (puerto == null || puerto.isBlank()) {
            throw new IllegalArgumentException("Selecciona el puerto COM de la impresora Bluetooth.");
        }

        SerialPort serial = SerialPort.getCommPort(puerto);
        // 8 bits de datos, 1 bit de parada, sin paridad (configuración estándar).
        serial.setComPortParameters(baudios, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serial.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        // Bloquea la escritura hasta 15 segundos si el puerto está lleno.
        serial.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 15000);

        if (!serial.openPort()) {
            throw new RuntimeException("No se pudo abrir el puerto " + puerto + ".\n"
                    + "Verifica que la impresora esté encendida, emparejada y que ningún "
                    + "otro programa esté usando ese puerto.");
        }

        try {
            int enviados = serial.writeBytes(datos, datos.length);
            if (enviados != datos.length) {
                throw new RuntimeException("Solo se enviaron " + enviados + " de "
                        + datos.length + " bytes al puerto " + puerto + ".");
            }
            // Pequeña espera para que la impresora termine de recibir antes de cerrar.
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Envío interrumpido al puerto " + puerto + ".", e);
        } finally {
            serial.closePort();
        }
    }

    // ============================================================
    //  Modo de prueba sin impresora
    // ============================================================
    /**
     * Guarda el ticket en Documentos\CineMax para revisarlo sin imprimir.
     *
     * @return la carpeta donde se guardaron los archivos
     */
    public static Path guardarPrueba(TicketVenta ticket) throws IOException {
        Path carpeta = Path.of(System.getProperty("user.home"), "Documents", "CineMax");
        Files.createDirectories(carpeta);
        Files.writeString(carpeta.resolve("boleta_prueba.txt"),
                TicketEscPos.vistaPrevia(ticket), StandardCharsets.UTF_8);
        Files.write(carpeta.resolve("boleta_prueba_escpos.bin"),
                TicketEscPos.generar(ticket));
        return carpeta;
    }
}
