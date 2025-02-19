import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class Mandelbrot extends JPanel {

    private static final int WIDTH = 1600; // Doppelte Breite
    private static final int HEIGHT = 1600; // Doppelte Höhe
    private static final int MAX_ITER = 1000;
    private static final double ZOOM = 400; // Zoom angepasst, um das Fraktal scharf darzustellen
    private BufferedImage image;

    public Mandelbrot() {
        image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        createFractal();
    }

    private void createFractal() {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                double zx = 0;
                double zy = 0;
                double cX = (x - WIDTH / 2) / ZOOM;
                double cY = (y - HEIGHT / 2) / ZOOM;
                int iter = MAX_ITER;

                // Iteriere, um zu überprüfen, ob der Punkt zur Mandelbrot-Menge gehört
                while (zx * zx + zy * zy < 4 && iter > 0) {
                    double tmp = zx * zx - zy * zy + cX;
                    zy = 2.0 * zx * zy + cY;
                    zx = tmp;
                    iter--;
                }

                // Farbgebung:
                // Wenn der Punkt zur Mandelbrot-Menge gehört (iter == 0), färbe ihn weiß
                // Andernfalls färbe ihn schwarz
                // Farbverlauf für divergierende Punkte
                if (iter == 0) {
                    image.setRGB(x, y, Color.WHITE.getRGB()); // Apfelmännchen weiß
                } else {
                    int color = iter % 256; // Farbwert basierend auf der Anzahl der Iterationen
                    image.setRGB(x, y, new Color(color, color, color).getRGB()); // Graustufen
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, this);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Mandelbrot Set - Apfelmännchen");
        Mandelbrot mandelbrot = new Mandelbrot();
        frame.add(mandelbrot);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}