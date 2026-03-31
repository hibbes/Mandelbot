import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Interaktiver Mandelbrot-Fraktal-Viewer mit Farbgebung, Zoom, Pan und PNG-Export.
 *
 * <h2>Was ist das Mandelbrot-Fraktal?</h2>
 * <p>Das Mandelbrot-Fraktal (auch "Apfelmännchen") ist eine Menge komplexer Zahlen c,
 * für die die Folge  z_{n+1} = z_n² + c  (mit z_0 = 0) beschränkt bleibt.</p>
 *
 * <p>Für jeden Bildpunkt (x, y) wird c = (x + yi) gesetzt und geprüft, ob die Folge
 * divergiert. Divergiert sie nach {@code MAX_ITER} Schritten noch nicht (|z| < 2),
 * gehört der Punkt zur Menge → wird weiß gefärbt. Andernfalls wird die Farbe nach
 * der Anzahl der benötigten Iterationen bestimmt (je schneller die Divergenz,
 * desto lebhafter die Farbe).</p>
 *
 * <h2>Bedienung</h2>
 * <ul>
 *   <li><b>Linksklick</b>: Hineinzoomen (Zoomfaktor 3×, zentriert auf Klickpunkt)</li>
 *   <li><b>Rechtsklick</b>: Herauszoomen</li>
 *   <li><b>Drag (linke Taste)</b>: Bildausschnitt verschieben (Pan)</li>
 *   <li><b>Mausrad</b>: stufenloses Zoomen</li>
 *   <li><b>Taste R</b>: Reset auf Startansicht</li>
 *   <li><b>Taste S</b>: Bild als PNG speichern (mandelbrot_export.png)</li>
 *   <li><b>Taste +/-</b>: Zoom-In / Zoom-Out per Tastatur</li>
 * </ul>
 *
 * <h2>Technische Umsetzung</h2>
 * <ul>
 *   <li>Multithreading: Das Bild wird in horizontale Streifen aufgeteilt und
 *       parallel mit einem {@link ExecutorService} berechnet.</li>
 *   <li>Smooth Coloring: Vermeidet scharfe Farbgrenzen durch Interpolation
 *       mit dem normierten Iterationswert (Potenzial-Funktion).</li>
 *   <li>HSB-Farbskala: Lebendige Regenbogenfarben statt Graustufen.</li>
 * </ul>
 *
 * @author hibbes (erweitert mit Zoom, Pan, Farbe, Multithreading)
 * @version 2.0
 */
public class Mandelbrot extends JPanel implements KeyListener {

    // ── Bildgröße ─────────────────────────────────────────────────────────────
    /** Breite des Fensters / Bildes in Pixeln. */
    private static final int WIDTH  = 900;
    /** Höhe des Fensters / Bildes in Pixeln. */
    private static final int HEIGHT = 900;

    // ── Fraktal-Parameter ─────────────────────────────────────────────────────
    /**
     * Maximale Anzahl Iterationen pro Punkt.
     * Höhere Werte → mehr Detail an den Rändern, aber langsamere Berechnung.
     */
    private static final int MAX_ITER = 512;

    // ── Ansichtsfenster (wird durch Zoom/Pan verändert) ───────────────────────
    /** Linke Grenze des sichtbaren Bereichs in der komplexen Ebene. */
    private double xMin = -2.5;
    /** Rechte Grenze des sichtbaren Bereichs in der komplexen Ebene. */
    private double xMax =  1.0;
    /** Untere Grenze des sichtbaren Bereichs in der komplexen Ebene. */
    private double yMin = -1.5;
    /** Obere Grenze des sichtbaren Bereichs in der komplexen Ebene. */
    private double yMax =  1.5;

    // ── Internes Bild ─────────────────────────────────────────────────────────
    /** Das berechnete Fraktalbild. Wird bei jedem Neuzeichnen aktualisiert. */
    private BufferedImage image;

    /** Wird während der Berechnung auf true gesetzt, um Doppelberechnungen zu vermeiden. */
    private volatile boolean rendering = false;

    // ── Pan-Zustand ───────────────────────────────────────────────────────────
    /** X-Koordinate des letzten Maus-Drücken-Events (für Pan). */
    private int dragStartX;
    /** Y-Koordinate des letzten Maus-Drücken-Events (für Pan). */
    private int dragStartY;

    // ── Startansicht (für Reset) ──────────────────────────────────────────────
    private static final double INIT_X_MIN = -2.5;
    private static final double INIT_X_MAX =  1.0;
    private static final double INIT_Y_MIN = -1.5;
    private static final double INIT_Y_MAX =  1.5;

    /**
     * Konstruktor: Initialisiert das Panel, Maus- und Tastatur-Listener.
     */
    public Mandelbrot() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);        // Damit Tastatureingaben empfangen werden
        addKeyListener(this);

        // ── Mausklick-Listener: Zoom-In (links), Zoom-Out (rechts) ────────────
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStartX = e.getX();
                dragStartY = e.getY();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // Klickpunkt in komplexe Koordinaten umrechnen
                double cx = xMin + (e.getX() / (double) WIDTH)  * (xMax - xMin);
                double cy = yMin + (e.getY() / (double) HEIGHT) * (yMax - yMin);

                if (SwingUtilities.isLeftMouseButton(e)) {
                    zoom(cx, cy, 1.0 / 3.0);   // Hineinzoomen: Bereich auf 1/3 verkleinern
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    zoom(cx, cy, 3.0);           // Herauszoomen: Bereich 3× vergrößern
                }
            }
        });

        // ── Maus-Drag-Listener: Pan ────────────────────────────────────────────
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // Verschiebung in Pixeln → in komplexe Koordinaten umrechnen
                double dx = (dragStartX - e.getX()) / (double) WIDTH  * (xMax - xMin);
                double dy = (dragStartY - e.getY()) / (double) HEIGHT * (yMax - yMin);
                xMin += dx; xMax += dx;
                yMin += dy; yMax += dy;
                dragStartX = e.getX();
                dragStartY = e.getY();
                renderAsync();          // Bild neu berechnen
            }
        });

        // ── Mausrad-Listener: stufenloses Zoomen ──────────────────────────────
        addMouseWheelListener(e -> {
            double factor = e.getWheelRotation() > 0 ? 1.2 : 0.8;  // raus / rein
            double cx = xMin + (e.getX() / (double) WIDTH)  * (xMax - xMin);
            double cy = yMin + (e.getY() / (double) HEIGHT) * (yMax - yMin);
            zoom(cx, cy, factor);
        });

        // Erstes Bild berechnen
        renderAsync();
    }

    /**
     * Verschiebt und skaliert das Ansichtsfenster um den Mittelpunkt (cx, cy).
     *
     * @param cx  X-Koordinate des Zoom-Zentrums (in der komplexen Ebene)
     * @param cy  Y-Koordinate des Zoom-Zentrums (in der komplexen Ebene)
     * @param factor &lt; 1.0 = Hineinzoomen, &gt; 1.0 = Herauszoomen
     */
    private void zoom(double cx, double cy, double factor) {
        double newW = (xMax - xMin) * factor;   // neue Breite des Ausschnitts
        double newH = (yMax - yMin) * factor;   // neue Höhe des Ausschnitts
        xMin = cx - newW / 2;
        xMax = cx + newW / 2;
        yMin = cy - newH / 2;
        yMax = cy + newH / 2;
        renderAsync();
    }

    /**
     * Startet die Fraktalberechnung in einem Hintergrund-Thread, damit die
     * Benutzeroberfläche während der Berechnung reaktionsfähig bleibt.
     * Mehrfache Aufrufe während einer laufenden Berechnung werden ignoriert.
     */
    private void renderAsync() {
        if (rendering) return;
        rendering = true;

        // Aktuelle Ansichtsparameter für den Thread sichern
        final double x0 = xMin, x1 = xMax, y0 = yMin, y1 = yMax;

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return createFractal(x0, x1, y0, y1);
            }

            @Override
            protected void done() {
                try {
                    image = get();          // berechnetes Bild übernehmen
                    repaint();              // Swing anweisen, das Panel neu zu zeichnen
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    rendering = false;      // nächste Berechnung freigeben
                }
            }
        }.execute();
    }

    /**
     * Berechnet das Mandelbrot-Fraktalbild für den angegebenen Ausschnitt.
     *
     * <p>Die Berechnung wird mit einem {@link ExecutorService} auf mehrere Threads
     * aufgeteilt (ein Thread pro CPU-Kern), wobei jeder Thread einen horizontalen
     * Streifen des Bildes übernimmt.</p>
     *
     * @param x0 linke Grenze des Ausschnitts
     * @param x1 rechte Grenze des Ausschnitts
     * @param y0 obere Grenze des Ausschnitts
     * @param y1 untere Grenze des Ausschnitts
     * @return das fertig berechnete {@link BufferedImage}
     */
    private BufferedImage createFractal(double x0, double x1, double y0, double y1) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        int cores = Runtime.getRuntime().availableProcessors();   // Anzahl CPU-Kerne
        ExecutorService pool = Executors.newFixedThreadPool(cores);

        // Bildberechnung in horizontale Streifen aufteilen
        for (int startY = 0; startY < HEIGHT; startY++) {
            final int row = startY;
            pool.submit(() -> {
                for (int px = 0; px < WIDTH; px++) {
                    // Pixelkoordinate → komplexe Zahl c = cX + cY·i
                    double cX = x0 + (px / (double) WIDTH)  * (x1 - x0);
                    double cY = y0 + (row / (double) HEIGHT) * (y1 - y0);

                    // Iteration: z_{n+1} = z_n² + c, gestartet mit z_0 = 0
                    double zx = 0, zy = 0;
                    int iter = 0;
                    while (zx * zx + zy * zy < 4.0 && iter < MAX_ITER) {
                        double tmp = zx * zx - zy * zy + cX;   // Realteil von z²+c
                        zy = 2.0 * zx * zy + cY;                // Imaginärteil
                        zx = tmp;
                        iter++;
                    }

                    img.setRGB(px, row, pickColor(iter, zx, zy));
                }
            });
        }

        pool.shutdown();
        try { pool.awaitTermination(60, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return img;
    }

    /**
     * Bestimmt die Farbe eines Pixels anhand der Iterationsanzahl.
     *
     * <p><b>Smooth Coloring (Anti-Banding):</b><br>
     * Statt der ganzzahligen Iterationsanzahl wird ein geglätteter Wert
     * {@code t} berechnet, der einen fließenden Übergang zwischen Farben
     * erzeugt und unschöne Farbbandstufen vermeidet:</p>
     * <pre>
     *   t = iter - log₂(log₂(|z|))
     * </pre>
     *
     * @param iter Anzahl der durchgeführten Iterationen
     * @param zx   Realteil von z nach der letzten Iteration
     * @param zy   Imaginärteil von z nach der letzten Iteration
     * @return RGB-Farbwert als int
     */
    private int pickColor(int iter, double zx, double zy) {
        // Punkte in der Menge (iter == MAX_ITER) → schwarz
        if (iter == MAX_ITER) return Color.BLACK.getRGB();

        // Smooth Coloring: normierter Iterationswert zwischen 0.0 und 1.0
        double log_zn = Math.log(zx * zx + zy * zy) / 2.0;   // log(|z|)
        double nu     = Math.log(log_zn / Math.log(2)) / Math.log(2);
        double t      = (iter + 1 - nu) / MAX_ITER;            // normiert [0,1]

        // HSB-Farbe: Farbton rotiert mit t, hohe Sättigung, helle Darstellung
        float hue        = (float) (t * 3.0 % 1.0);   // mehrfacher Farbzyklus
        float saturation = 0.85f;
        float brightness = (float) Math.min(1.0, t * 5.0);  // hell nahe der Menge
        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    // ── Swing-Rendering ───────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, this);   // fertig berechnetes Bild anzeigen
        }
        // Koordinaten-Info in der Titelzeile aktualisieren
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            double zoom = 3.5 / (xMax - xMin);   // relativer Zoom gegenüber Startansicht
            frame.setTitle(String.format(
                "Mandelbrot  |  x:[%.4f, %.4f]  y:[%.4f, %.4f]  Zoom: %.1f×",
                xMin, xMax, yMin, yMax, zoom));
        }
    }

    // ── Tastatur-Steuerung ────────────────────────────────────────────────────

    @Override
    public void keyPressed(KeyEvent e) {
        double cx = (xMin + xMax) / 2;   // aktueller Mittelpunkt
        double cy = (yMin + yMax) / 2;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_ADD:
                zoom(cx, cy, 0.5);   // Hineinzoomen
                break;
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_SUBTRACT:
                zoom(cx, cy, 2.0);   // Herauszoomen
                break;
            case KeyEvent.VK_R:
                // Reset: Startansicht wiederherstellen
                xMin = INIT_X_MIN; xMax = INIT_X_MAX;
                yMin = INIT_Y_MIN; yMax = INIT_Y_MAX;
                renderAsync();
                break;
            case KeyEvent.VK_S:
                savePNG();           // Bild als PNG speichern
                break;
        }
    }

    @Override public void keyTyped(KeyEvent e)    {}
    @Override public void keyReleased(KeyEvent e) {}

    /**
     * Speichert das aktuelle Bild als PNG-Datei im Programmverzeichnis.
     * Der Dateiname enthält die aktuellen Koordinaten für spätere Referenz.
     */
    private void savePNG() {
        if (image == null) return;
        String filename = String.format("mandelbrot_x%.3f_y%.3f_z%.0f.png",
                (xMin + xMax) / 2, (yMin + yMax) / 2, 3.5 / (xMax - xMin));
        try {
            ImageIO.write(image, "PNG", new File(filename));
            JOptionPane.showMessageDialog(this, "Gespeichert als:\n" + filename,
                    "PNG exportiert", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Speichern: " + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Programmeinstieg ──────────────────────────────────────────────────────

    /**
     * Erstellt das Fenster und startet den Fraktal-Viewer.
     *
     * @param args Kommandozeilenargumente (nicht verwendet)
     */
    public static void main(String[] args) {
        // Swing muss im Event Dispatch Thread initialisiert werden
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Mandelbrot-Fraktal");
            Mandelbrot panel = new Mandelbrot();
            frame.add(panel);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);   // Fenster mittig auf dem Bildschirm
            frame.setVisible(true);
            panel.requestFocusInWindow();        // Tastatureingaben aktivieren
        });
    }
}
