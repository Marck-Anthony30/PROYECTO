# Impresión de boletas en POS térmica de 58 mm

Documentación del módulo de impresión de CineMax. Está pensado **solo para
impresoras térmicas POS de 58 mm** (32 columnas de ancho).

> ⚠️ **Alcance:** es una **prueba académica**. No es un comprobante electrónico
> real: no se firma digitalmente ni se envía a SUNAT. El ticket muestra la
> leyenda `*** DOCUMENTO DE PRUEBA ***`.

---

## 1. Idea general

La boleta se puede imprimir de dos formas, y el usuario solo elige una:

| Modo | Cómo funciona |
|---|---|
| **USB** | La POS está instalada en Windows y se le envían los bytes con `javax.print` (incluido en Java). |
| **Bluetooth** | Windows crea un puerto COM al emparejar la POS. Se abre ese puerto con **jSerialComm**, se escriben los bytes y se cierra. |

El ticket siempre se genera a **32 columnas**, el ancho del papel de 58 mm.

---

## 2. Qué se agregó

- **Ventas → Imprimir boleta**: imprime la venta actual (registrada o no).
- **Historial → Imprimir boleta**: reimprime cualquier venta guardada.
- Al **Registrar venta**, el sistema ofrece imprimir la boleta.
- El diálogo tiene **dos opciones claras**:
  - `USB (impresora de Windows)`
  - `Bluetooth (puerto COM)` con lista de puertos y velocidad.

Recuerda la última opción usada, para que la siguiente impresión sea más rápida.

---

## 3. Archivos

| Archivo | Qué hace |
|---|---|
| `print/TicketVenta.java` | Datos de la boleta, texto del QR y fábrica `desde(...)`. |
| `print/TicketEscPos.java` | Texto de vista previa (32 columnas) y bytes ESC/POS con el QR. |
| `print/ImpresoraTermica.java` | Lista impresoras USB y puertos COM; imprime por los dos caminos. |
| `components/DialogoImpresion.java` | Diálogo con USB/Bluetooth, vista previa y botones. |
| `controller/VentaController.java` | Botón, aviso al registrar y armado del ticket. |
| `controller/HistorialController.java` | Botón para reimprimir una venta del historial. |
| `view/ventas.fxml`, `view/historial.fxml` | Botones **Imprimir boleta**. |

Dependencia agregada al `pom.xml`:

```xml
<dependency>
    <groupId>com.fazecast</groupId>
    <artifactId>jSerialComm</artifactId>
    <version>2.11.0</version>
</dependency>
```

Es la única librería externa del módulo: sirve para hablar con el puerto COM del
Bluetooth. La impresión USB no necesita nada extra.

---

## 4. Cómo funciona

```
Ventas / Historial
        │  arma TicketVenta (TicketVenta.desde)
        ▼
DialogoImpresion.mostrar(ticket)
        │
        ├─ TicketEscPos.vistaPrevia()  → texto de 32 columnas (pantalla)
        ├─ TicketEscPos.generar()      → bytes ESC/POS (texto + QR)
        │
        ├─ USB       → ImpresoraTermica.imprimirUsb()       → javax.print
        └─ Bluetooth → ImpresoraTermica.imprimirBluetooth() → jSerialComm
```

### Por qué el QR no necesita imágenes

Las POS entienden el comando **ESC/POS `GS ( k`**, que dibuja el QR en la propia
impresora. Solo se le envía el texto y ella lo genera.

### Qué hace el envío Bluetooth

```java
SerialPort serial = SerialPort.getCommPort("COM5");
serial.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
serial.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
serial.openPort();
serial.writeBytes(datos, datos.length);  // ticket completo
Thread.sleep(500);                       // deja que la impresora reciba
serial.closePort();
```

Abrir y cerrar el puerto así es mucho más confiable que copiar archivos al COM.

---

## 5. Requisitos y configuración

1. Encender la impresora POS de 58 mm.
2. **USB:** conectarla e instalar el driver del fabricante (o el driver genérico
   **"Generic / Text Only"** de Windows).
3. **Bluetooth:** emparejarla desde **Configuración → Bluetooth y dispositivos**.
   Windows le asigna un puerto **COM** (por ejemplo `COM5`). Se puede ver en
   **Administrador de dispositivos → Puertos (COM y LPT)**, donde suele aparecer
   como *"Standard Serial over Bluetooth link (COM5)"*.
4. Abrir CineMax y usar **Imprimir boleta**.

### ¿Qué puerto COM elijo?

En el diálogo aparecen todos los puertos con su descripción:

```
Standard Serial over Bluetooth link (COM4)
Standard Serial over Bluetooth link (COM5)
```

Si no sabes cuál es, selecciona uno y pulsa **Probar**: el sistema abre y cierra
el puerto sin imprimir. Si responde, ese es el puerto correcto. Si dice que no
se pudo abrir, prueba el otro o pulsa **Actualizar**.

> Si hay varias impresoras USB, elegir la POS (`POS-58`, `POS58`, `58mm`,
> `Thermal`). El programa las ordena primero automáticamente.

---

## 6. Uso paso a paso

### Desde Ventas

1. Iniciar sesión (`admin / admin123`).
2. Ir a **Ventas**, elegir cliente y agregar productos.
3. Pulsar **Imprimir boleta**.
4. Elegir **USB** o **Bluetooth**, revisar la vista previa y pulsar **Imprimir**.
   - En Bluetooth, si no sabes qué COM es, pulsa **Probar** para verificar la
     conexión sin gastar papel.

### Desde Historial

1. Ir a **Historial**.
2. Seleccionar una venta de la tabla superior.
3. Pulsar **Imprimir boleta**.
4. Elegir el modo y pulsar **Imprimir**.

### Probar sin impresora

En el diálogo, pulsar **Guardar prueba**. Se crean en
`C:\Users\<usuario>\Documents\CineMax\`:

| Archivo | Contenido |
|---|---|
| `boleta_prueba.txt` | El ticket tal como se ve en pantalla. |
| `boleta_prueba_escpos.bin` | Los bytes que se enviarían a la impresora. |

---

## 7. Contenido del ticket

```
         CINEMAX S.A.C.
        RUC: 20123456789
  Av. Los Cines 123 - Juliaca
      Tel: (051) 321-4567
--------------------------------
       BOLETA ELECTRONICA
        B001 - 00000012
Fecha: 13/11/2026 20:15
Cliente: Juan Perez
DNI: 12345678
Cajero: Administrador
--------------------------------
CANT. DESCRIPCION
--------------------------------
2 x Entrada 2D Adulto
    F1 - Avatar - 13/11/2026
    20:00
    Asientos: A1,A2
    P.U. S/ 15.00
    TOTAL S/ 30.00
1 x Canchita grande
    P.U. S/ 12.00
    TOTAL S/ 12.00
--------------------------------
Neto:                   S/ 35.59
IGV (18%):               S/ 6.41
TOTAL:                  S/ 42.00
--------------------------------
         [ CODIGO QR ]
     Gracias por su compra
  Representacion impresa de la
       BOLETA ELECTRONICA
  *** DOCUMENTO DE PRUEBA ***
```

- **32 columnas** fijas (papel de 58 mm).
- Se quitan tildes y `ñ` (`Película → Pelicula`) porque las impresoras
  económicas suelen imprimir mal esos caracteres.
- Los textos largos se recortan o se parten en varias líneas.

---

## 8. El código QR "tipo SUNAT"

Contiene un texto separado por barras, con el orden de campos de SUNAT
(versión simplificada):

```
20123456789|03|B001|00000012|6.41|42.00|13/11/2026|1|12345678|42.00|TEST|
```

| # | Campo | Ejemplo | Significado |
|---|---|---|---|
| 1 | RUC del emisor | `20123456789` | RUC del cine |
| 2 | Tipo de comprobante | `03` | 03 = Boleta (01 = Factura) |
| 3 | Serie | `B001` | Serie de la boleta |
| 4 | Número | `00000012` | Correlativo |
| 5 | IGV | `6.41` | Monto del IGV |
| 6 | Total | `42.00` | Importe total |
| 7 | Fecha de emisión | `13/11/2026` | dd/MM/yyyy |
| 8 | Tipo de documento | `1` | 1 = DNI |
| 9 | Número de documento | `12345678` | DNI del cliente |
| 10 | Importe total | `42.00` | Repetido como en SUNAT |
| 11 | Marca de prueba | `TEST` | Indica que es simulación |

---

## 9. Comandos ESC/POS usados

| Bytes | Comando | Para qué |
|---|---|---|
| `1B 40` | `ESC @` | Reiniciar la impresora |
| `1B 61 00/01` | `ESC a n` | Alinear a la izquierda / centrar |
| `1B 21 10` / `00` | `ESC ! n` | Doble altura / normal |
| `1B 45 01/00` | `ESC E n` | Negrita activada / desactivada |
| `1D 28 6B … 31 41 32 00` | `GS ( k` | QR modelo 2 |
| `1D 28 6B … 31 43 06` | `GS ( k` | Tamaño del módulo (6 puntos) |
| `1D 28 6B … 31 45 31` | `GS ( k` | Corrección de errores M |
| `1D 28 6B pL pH 31 50 30` | `GS ( k` | Guardar contenido del QR |
| `1D 28 6B … 31 51 30` | `GS ( k` | Imprimir el QR |
| `1B 64 04` | `ESC d n` | Avanzar 4 líneas |
| `1D 56 42 00` | `GS V B 0` | Cortar papel |

Si la impresora **no tiene cortador** y sale basura al final, cambiar
`CORTAR_PAPEL = false` en `TicketEscPos.java`.

---

## 10. Cómo explicarlo en la exposición (2 minutos)

> "El sistema no solo registra la venta: también emite la boleta. Para eso
> agregamos un módulo de impresión para la POS térmica de 58 mm que se usa en
> boletería."

1. **Mostrar el botón** en Ventas y decir que también está en Historial para
   reimprimir ventas antiguas.
2. **Abrir el diálogo** y señalar la vista previa: "32 columnas, exactamente el
   ancho del papel de 58 mm".
3. **Explicar las dos conexiones**:
   - "USB: uso la impresora instalada en Windows y `javax.print`."
   - "Bluetooth: uso **jSerialComm** para abrir el puerto COM que crea Windows,
     escribir el ticket y cerrarlo. Así no depende de copiar archivos."
4. **Señalar el QR**: "contiene RUC, tipo, serie, número, IGV, total, fecha y
   DNI, en el orden de SUNAT. Es una simulación académica".
5. **Imprimir** y mostrar el ticket.
6. **Cerrar**: "la impresora dibuja el QR con su propio comando ESC/POS, por eso
   no necesitamos generar imágenes".

### Preguntas posibles

**¿Por qué usaron jSerialComm?**
Porque Java estándar no puede abrir un puerto COM de forma cómoda. La librería
abre el puerto, escribe los bytes y lo cierra de forma confiable.

**¿Cómo sé qué puerto elegir?**
En Windows: *Administrador de dispositivos → Puertos (COM y LPT)*. El diálogo
muestra la descripción, por ejemplo *"Standard Serial over Bluetooth link
(COM5)"*.

**¿Se conecta con SUNAT?**
No. Es académico: imita el formato del QR, pero no hay firma ni envío.

**¿Dónde cambio el RUC o la dirección?**
En las constantes de `TicketVenta.java`.

---

## 11. Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| No aparece la impresora USB | No está instalada en Windows | Instalarla con su driver o "Generic / Text Only" |
| Imprime en blanco | Se eligió "Microsoft Print to PDF" | Elegir la POS (POS-58) |
| Bluetooth no imprime | Puerto equivocado | Pulsar **Probar** en cada COM y usar el que responda |
| "No se pudo abrir el puerto" | Otra app lo está usando | Cerrar esa app o reiniciar la impresora |
| No aparece ningún COM | No está emparejada | Emparejar en Windows y pulsar **Actualizar** |
| Imprime caracteres raros | Velocidad incorrecta | Probar 115200 en el combo de velocidad |
| El QR sale borroso | Módulo muy chico | En `TicketEscPos.qr`, cambiar `0x06` por `0x08` |
| Basura al final | La impresora no tiene cortador | `CORTAR_PAPEL = false` |
| Acentos mal impresos | Codificación del equipo | Ya se convierten a ASCII |

---

## 12. Límites

- No genera XML UBL ni firma digital.
- El número de boleta usa el `id_venta`, no un correlativo fiscal.
- El formato es fijo para 58 mm (32 columnas).

---

## 13. Configuración rápida

| Qué cambiar | Dónde |
|---|---|
| RUC, razón social, dirección, teléfono | `TicketVenta.java` (constantes) |
| Ancho del ticket (32 = 58 mm) | `TicketEscPos.ANCHO` |
| Cortar o no el papel | `TicketEscPos.CORTAR_PAPEL` |
| Tamaño del QR | `TicketEscPos.qr()` → byte `0x06` |
| Texto del QR | `TicketVenta.textoQr()` |
| Velocidad Bluetooth | Combo del diálogo (9600 por defecto) |
