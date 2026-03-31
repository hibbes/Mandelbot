# Mandelbot – Interaktiver Mandelbrot-Fraktal-Viewer

Ein interaktiver Java-Viewer für das Mandelbrot-Fraktal ("Apfelmännchen") mit Echtzeit-Zoom, Pan, Smooth Coloring und PNG-Export.

## Bedienung

| Aktion | Funktion |
|--------|----------|
| **Linksklick** | Hineinzoomen (3×, zentriert auf Klickpunkt) |
| **Rechtsklick** | Herauszoomen |
| **Drag** (linke Taste) | Bildausschnitt verschieben |
| **Mausrad** | Stufenloses Zoomen |
| **Taste R** | Auf Startansicht zurücksetzen |
| **Taste S** | Aktuelles Bild als PNG exportieren |
| **Taste + / -** | Zoom-In / Zoom-Out per Tastatur |

## Was ist das Mandelbrot-Fraktal?

Für jeden Bildpunkt (x, y) wird die komplexe Zahl `c = x + y·i` gebildet und die Folge berechnet:

```
z₀ = 0
zₙ₊₁ = zₙ² + c
```

- Bleibt `|z|` beschränkt (≤ 2 nach MAX_ITER Iterationen) → Punkt **gehört zur Menge** → schwarz
- Divergiert die Folge → Farbe nach Divergenzgeschwindigkeit (je schneller, desto heller/bunter)

## Technische Highlights

- **Smooth Coloring**: Normierter Iterationswert verhindert scharfe Farbstufen
- **Multithreading**: Berechnung auf alle CPU-Kerne verteilt (ein Thread pro Zeile)
- **SwingWorker**: UI bleibt während der Berechnung reaktionsfähig
- **HSB-Farbskala**: Lebendige Regenbogenfarben statt Graustufen

## Mögliche Erweiterungen

- Julia-Mengen-Visualisierung (c fest, z₀ variabel)
- Lesezeichen für interessante Koordinaten speichern
- Animierter Zoom-Flug entlang einer Koordinatenfolge
