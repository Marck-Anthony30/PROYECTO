package pe.edu.upeu.cinemax.components;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import pe.edu.upeu.cinemax.print.ImpresoraTermica;
import pe.edu.upeu.cinemax.print.TicketEscPos;
import pe.edu.upeu.cinemax.print.TicketVenta;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Diálogo para imprimir la boleta en una POS de 58 mm.
 *
 * <p>El usuario solo elige el tipo de conexión:</p>
 * <ul>
 *   <li><b>USB:</b> una impresora instalada en Windows.</li>
 *   <li><b>Bluetooth:</b> el puerto COM que crea Windows al emparejarla.</li>
 * </ul>
 * Se recuerda la última elección para que la siguiente impresión sea más rápida.
 */
public final class DialogoImpresion {

    // Se recuerda entre impresiones para no volver a elegir todo cada vez.
    private static boolean ultimoBluetooth = false;
    private static String ultimaImpresora = null;
    private static String ultimoPuerto = null;
    private static int ultimaVelocidad = 9600;

    private DialogoImpresion() {
    }

    /** Abre el diálogo para el ticket indicado. */
    public static void mostrar(TicketVenta ticket) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Imprimir boleta · POS 58 mm");
        dialog.setHeaderText("Elige USB o Bluetooth, revisa la vista previa y pulsa Imprimir.");

        // ---------- USB ----------
        RadioButton rbUsb = new RadioButton("USB (impresora de Windows)");
        ComboBox<ImpresoraTermica.Impresora> cbxUsb = new ComboBox<>();
        cbxUsb.setMaxWidth(Double.MAX_VALUE);
        cbxUsb.setItems(FXCollections.observableArrayList(ImpresoraTermica.impresoras()));
        seleccionarImpresora(cbxUsb);

        // ---------- Bluetooth ----------
        RadioButton rbBluetooth = new RadioButton("Bluetooth (puerto COM)");
        ComboBox<ImpresoraTermica.Puerto> cbxBt = new ComboBox<>();
        cbxBt.setMaxWidth(Double.MAX_VALUE);
        cbxBt.setItems(FXCollections.observableArrayList(ImpresoraTermica.puertosCom()));
        seleccionarPuerto(cbxBt);

        Button btnActualizar = new Button("Actualizar");
        btnActualizar.setOnAction(e -> {
            cbxBt.setItems(FXCollections.observableArrayList(ImpresoraTermica.puertosCom()));
            seleccionarPuerto(cbxBt);
        });

        ComboBox<Integer> cbxVelocidad = new ComboBox<>(
                FXCollections.observableArrayList(9600, 19200, 38400, 57600, 115200));
        cbxVelocidad.setValue(ultimaVelocidad);
        cbxVelocidad.setPrefWidth(110);

        Button btnProbar = new Button("Probar");
        btnProbar.setOnAction(e -> {
            ImpresoraTermica.Puerto puerto = cbxBt.getValue();
            if (puerto == null) {
                Dialogos.informar("Sin puerto", "No se detectó ningún puerto COM.");
                return;
            }
            int velocidad = cbxVelocidad.getValue() == null ? 9600 : cbxVelocidad.getValue();
            try {
                ImpresoraTermica.probarBluetooth(puerto.nombre(), velocidad);
                Dialogos.informar("Conexión correcta",
                        "El puerto " + puerto.nombre() + " respondió a " + velocidad + " baudios.\n"
                                + "Si al imprimir no sale nada, prueba la otra velocidad o el otro puerto COM.");
            } catch (Exception ex) {
                Dialogos.informar("No se pudo abrir el puerto", ex.getMessage());
            }
        });

        HBox botonesBluetooth = new HBox(6, btnActualizar, btnProbar);

        ToggleGroup grupo = new ToggleGroup();
        rbUsb.setToggleGroup(grupo);
        rbBluetooth.setToggleGroup(grupo);

        // Si no hay impresoras USB pero sí puertos COM, arranca en Bluetooth.
        if (cbxUsb.getItems().isEmpty() && !cbxBt.getItems().isEmpty()) {
            rbBluetooth.setSelected(true);
        } else {
            rbUsb.setSelected(!ultimoBluetooth);
            rbBluetooth.setSelected(ultimoBluetooth);
        }

        cbxUsb.disableProperty().bind(rbBluetooth.selectedProperty());
        cbxBt.disableProperty().bind(rbUsb.selectedProperty());
        botonesBluetooth.disableProperty().bind(rbUsb.selectedProperty());
        cbxVelocidad.disableProperty().bind(rbUsb.selectedProperty());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints colEtiqueta = new ColumnConstraints();
        colEtiqueta.setMinWidth(190);
        ColumnConstraints colCampo = new ColumnConstraints();
        colCampo.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(colEtiqueta, colCampo, new ColumnConstraints());

        grid.add(rbUsb, 0, 0);
        grid.add(cbxUsb, 1, 0, 2, 1);
        grid.add(rbBluetooth, 0, 1);
        grid.add(cbxBt, 1, 1);
        grid.add(botonesBluetooth, 2, 1);
        grid.add(new Label("Velocidad Bluetooth:"), 0, 2);
        grid.add(cbxVelocidad, 1, 2);
        grid.add(new Label("9600 es lo habitual"), 2, 2);

        Label ayuda = new Label("USB: la POS debe estar instalada en Windows. "
                + "Bluetooth: la POS debe estar emparejada y mostrar su puerto COM.");
        ayuda.setWrapText(true);
        ayuda.getStyleClass().add("texto-ayuda");

        TextArea vistaPrevia = new TextArea(TicketEscPos.vistaPrevia(ticket));
        vistaPrevia.setEditable(false);
        vistaPrevia.setWrapText(false);
        vistaPrevia.setPrefRowCount(22);
        vistaPrevia.setPrefColumnCount(44);
        vistaPrevia.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");

        VBox contenido = new VBox(10, grid, ayuda, vistaPrevia);
        contenido.setPadding(new Insets(10));
        contenido.setPrefWidth(500);
        dialog.getDialogPane().setContent(contenido);

        ButtonType imprimir = new ButtonType("Imprimir", ButtonBar.ButtonData.OK_DONE);
        ButtonType guardar = new ButtonType("Guardar prueba", ButtonBar.ButtonData.OTHER);
        ButtonType cerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(imprimir, guardar, cerrar);
        Dialogos.aplicarEstilo(dialog.getDialogPane());

        Optional<ButtonType> resultado = dialog.showAndWait();
        if (resultado.isEmpty() || resultado.get() == cerrar) {
            return;
        }

        // ---- Guardar el ticket en Documentos\CineMax (modo de prueba) ----
        if (resultado.get() == guardar) {
            try {
                Path carpeta = ImpresoraTermica.guardarPrueba(ticket);
                Dialogos.informar("Ticket guardado",
                        "Se guardaron boleta_prueba.txt y boleta_prueba_escpos.bin en:\n" + carpeta);
            } catch (Exception e) {
                e.printStackTrace();
                Dialogos.informar("No se pudo guardar", e.getMessage());
            }
            return;
        }

        // ---- Imprimir por USB o Bluetooth ----
        byte[] datos = TicketEscPos.generar(ticket);
        try {
            if (rbBluetooth.isSelected()) {
                ImpresoraTermica.Puerto puerto = cbxBt.getValue();
                if (puerto == null) {
                    Dialogos.informar("Sin puerto Bluetooth",
                            "No se detectó ningún puerto COM.\n"
                                    + "Empareja la impresora en Windows y pulsa Actualizar.");
                    return;
                }
                int velocidad = cbxVelocidad.getValue() == null ? 9600 : cbxVelocidad.getValue();
                ImpresoraTermica.imprimirBluetooth(puerto.nombre(), velocidad, datos);
                ultimoBluetooth = true;
                ultimoPuerto = puerto.nombre();
                ultimaVelocidad = velocidad;
            } else {
                ImpresoraTermica.Impresora impresora = cbxUsb.getValue();
                if (impresora == null) {
                    Dialogos.informar("Sin impresora USB",
                            "No hay impresoras instaladas en Windows.\n"
                                    + "Instala la POS de 58 mm y vuelve a intentar.");
                    return;
                }
                ImpresoraTermica.imprimirUsb(impresora, datos);
                ultimoBluetooth = false;
                ultimaImpresora = impresora.nombre();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Dialogos.informar("No se pudo imprimir", e.getMessage()
                    + "\n\nRevisa que la impresora esté encendida y conectada.");
        }
    }

    /** Selecciona la impresora usada la última vez o la primera POS de la lista. */
    private static void seleccionarImpresora(ComboBox<ImpresoraTermica.Impresora> cbx) {
        if (cbx.getItems().isEmpty()) return;
        if (ultimaImpresora != null) {
            for (ImpresoraTermica.Impresora impresora : cbx.getItems()) {
                if (impresora.nombre().equals(ultimaImpresora)) {
                    cbx.getSelectionModel().select(impresora);
                    return;
                }
            }
        }
        cbx.getSelectionModel().selectFirst();
    }

    /** Selecciona el puerto usado la última vez o el primero de la lista. */
    private static void seleccionarPuerto(ComboBox<ImpresoraTermica.Puerto> cbx) {
        if (cbx.getItems().isEmpty()) return;
        if (ultimoPuerto != null) {
            for (ImpresoraTermica.Puerto puerto : cbx.getItems()) {
                if (puerto.nombre().equals(ultimoPuerto)) {
                    cbx.getSelectionModel().select(puerto);
                    return;
                }
            }
        }
        cbx.getSelectionModel().selectFirst();
    }
}
