package pe.edu.upeu.cinemax.print;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.edu.upeu.cinemax.model.Venta;
import pe.edu.upeu.cinemax.model.VentaDetalle;
import pe.edu.upeu.cinemax.util.Formato;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Datos necesarios para imprimir una boleta de prueba en una impresora térmica
 * POS de 58 mm.
 *
 * <p><b>Importante:</b> esta clase NO genera un comprobante electrónico real ni
 * se conecta con SUNAT. Solo reúne la información de la venta para armar el
 * texto del ticket y el contenido del código QR "tipo SUNAT" que se usa en la
 * demostración.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketVenta {

    // ===== Datos del emisor (cámbialos por los datos reales del cine) =====
    public static final String RAZON_SOCIAL = "CINEMAX S.A.C.";
    public static final String RUC = "20123456789";
    public static final String DIRECCION = "Av. Los Cines 123 - Juliaca";
    public static final String TELEFONO = "(051) 321-4567";

    private String tipoComprobante; // "BOLETA ELECTRONICA"
    private String serie;           // "B001"
    private String numero;          // "00000012"
    private String fecha;           // "13/11/2026"
    private String hora;            // "20:15"
    private String clienteNombre;
    private String clienteDni;
    private String cajero;
    private List<VentaDetalle> detalles;
    private BigDecimal neto;
    private BigDecimal igv;
    private BigDecimal total;

    /** Si es {@code true} el ticket muestra la leyenda "DOCUMENTO DE PRUEBA". */
    @Builder.Default
    private boolean prueba = true;

    /**
     * NUEVA FUNCIÓN: arma la boleta a partir de una venta y sus detalles.
     * La usan tanto Ventas (al registrar o previsualizar) como Historial (para
     * reimprimir una venta ya guardada).
     *
     * @param venta         venta con sus totales (puede tener id nulo si aún no se registró)
     * @param detalles      líneas de la venta
     * @param clienteNombre nombre del cliente (si es nulo se usa "CLIENTE VARIOS")
     * @param cajero        usuario que atendió (si es nulo se usa "CAJERO")
     */
    public static TicketVenta desde(Venta venta, List<VentaDetalle> detalles,
                                    String clienteNombre, String cajero) {
        LocalDate fecha = venta.getFechaVenta() == null ? LocalDate.now() : venta.getFechaVenta();
        return TicketVenta.builder()
                .tipoComprobante("BOLETA ELECTRONICA")
                .serie("B001")
                .numero(venta.getIdVenta() == null ? "00000000"
                        : String.format("%08d", venta.getIdVenta()))
                .fecha(Formato.fecha(fecha))
                .hora(Formato.hora(LocalTime.now()))
                .clienteNombre(clienteNombre == null ? "CLIENTE VARIOS" : clienteNombre)
                .clienteDni(venta.getDniCliente() == null ? "" : venta.getDniCliente())
                .cajero(cajero == null ? "CAJERO" : cajero)
                .detalles(detalles)
                .neto(venta.getNetoTotal())
                .igv(venta.getIgv())
                .total(venta.getPrecioTotal())
                .build();
    }

    /**
     * Arma el texto que va DENTRO del código QR, con el mismo orden de campos
     * que usa SUNAT para los comprobantes electrónicos (versión simplificada):
     *
     * <pre>
     * RUC | Tipo | Serie | Número | IGV | Total | Fecha | TipoDoc | NroDoc | Total | TEST |
     * </pre>
     *
     * El último campo "TEST" deja claro que es una simulación académica.
     */
    public String textoQr() {
        return RUC + "|03|" + texto(serie) + "|" + texto(numero) + "|"
                + monto(igv) + "|" + monto(total) + "|" + texto(fecha) + "|"
                + "1|" + texto(clienteDni) + "|" + monto(total) + "|TEST|";
    }

    private static String monto(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
