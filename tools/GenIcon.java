import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the Voltworks Robots logo: a robot face on the Voltworks blue steel plate,
 * with the Voltworks gear-and-bolt mark as a badge in the corner.
 * Usage: java tools/GenIcon.java <project root>
 * Writes docs/images/logo.png (400px, CurseForge) and src/main/resources/assets/robots/icon.png (128px, mod list).
 */
public class GenIcon {
	static final Color OUTLINE = new Color(20, 24, 32);
	static final Color ACCENT = new Color(70, 200, 80);
	static final Color ACCENT_DARK = new Color(40, 140, 55);

	public static void main(String[] args) throws Exception {
		File root = new File(args.length > 0 ? args[0] : ".");
		BufferedImage big = draw(400);
		write(big, new File(root, "docs/images/logo.png"));
		write(scale(big, 128), new File(root, "src/main/resources/assets/robots/icon.png"));
		System.out.println("done");
	}

	static BufferedImage draw(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.scale(s / 100.0, s / 100.0); // design on a 100x100 grid

		// Plate: same rounded blue-steel square as the Voltworks logo
		RoundRectangle2D plate = new RoundRectangle2D.Double(3, 3, 94, 94, 22, 22);
		g.setPaint(new GradientPaint(0, 3, new Color(78, 96, 122), 0, 97, new Color(34, 42, 56)));
		g.fill(plate);
		g.setStroke(new BasicStroke(3f));
		g.setColor(OUTLINE);
		g.draw(plate);
		g.setStroke(new BasicStroke(1.2f));
		g.setColor(new Color(255, 255, 255, 50));
		g.draw(new RoundRectangle2D.Double(6, 6, 88, 88, 18, 18));

		// Hazard stripe along the bottom
		Shape oldClip = g.getClip();
		g.clip(new RoundRectangle2D.Double(6, 80, 88, 11, 6, 6));
		g.setColor(new Color(25, 25, 25));
		g.fill(new Rectangle2D.Double(0, 80, 100, 11));
		g.setColor(new Color(240, 180, 40));
		for (int i = -2; i < 14; i++) {
			Path2D stripe = new Path2D.Double();
			double x = i * 9;
			stripe.moveTo(x, 91);
			stripe.lineTo(x + 4.5, 91);
			stripe.lineTo(x + 15.5, 80);
			stripe.lineTo(x + 11, 80);
			stripe.closePath();
			g.fill(stripe);
		}
		g.setClip(oldClip);

		robotFace(g);

		// Voltworks mark as a badge, bottom right
		g.setColor(new Color(0, 0, 0, 90));
		g.fill(new Ellipse2D.Double(63.5, 61.5, 33, 33));
		g.setPaint(new GradientPaint(0, 60, new Color(60, 74, 96), 0, 93, new Color(30, 37, 50)));
		g.fill(new Ellipse2D.Double(62, 60, 33, 33));
		g.setColor(OUTLINE);
		g.setStroke(new BasicStroke(2f));
		g.draw(new Ellipse2D.Double(62, 60, 33, 33));
		AffineTransform old = g.getTransform();
		g.translate(78.5, 76.5);
		g.scale(0.36, 0.36);
		g.translate(-50, -46);
		voltworksMark(g);
		g.setTransform(old);

		g.dispose();
		return img;
	}

	/** Blocky robot head: antenna, ear caps, dark visor with glowing eyes, speaker grille. */
	static void robotFace(Graphics2D g) {
		// Antenna
		g.setColor(OUTLINE);
		g.fill(new Rectangle2D.Double(47.5, 12, 5, 10));
		g.setColor(new Color(120, 128, 140));
		g.fill(new Rectangle2D.Double(48.8, 13, 2.4, 9));
		box(g, 44.5, 6, 11, 8, 2.5, new Color(110, 230, 120), ACCENT_DARK);

		// Ear caps
		box(g, 13.5, 35, 9, 20, 2.5, ACCENT, ACCENT_DARK);
		box(g, 77.5, 35, 9, 20, 2.5, ACCENT, ACCENT_DARK);

		// Head
		RoundRectangle2D head = new RoundRectangle2D.Double(21, 20, 58, 54, 9, 9);
		g.setColor(new Color(0, 0, 0, 100));
		g.fill(new RoundRectangle2D.Double(23, 22, 58, 54, 9, 9));
		g.setPaint(new GradientPaint(0, 20, new Color(214, 219, 226), 0, 74, new Color(142, 150, 162)));
		g.fill(head);
		g.setColor(OUTLINE);
		g.setStroke(new BasicStroke(2.4f));
		g.draw(head);
		g.setColor(new Color(255, 255, 255, 120));
		g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Double(27, 23.2, 73, 23.2));
		// Rivets
		g.setColor(new Color(95, 102, 114));
		for (double[] p : new double[][]{{25.5, 59}, {25.5, 68}, {74.5, 59}}) {
			g.fill(new Ellipse2D.Double(p[0] - 1.3, p[1] - 1.3, 2.6, 2.6));
		}

		// Visor
		RoundRectangle2D visor = new RoundRectangle2D.Double(27.5, 28, 45, 25, 6, 6);
		g.setPaint(new GradientPaint(0, 28, new Color(14, 17, 23), 0, 53, new Color(34, 40, 52)));
		g.fill(visor);
		g.setColor(OUTLINE);
		g.setStroke(new BasicStroke(1.8f));
		g.draw(visor);

		// Eyes: soft glow, then the bright square
		for (double ex : new double[]{33.5, 54.5}) {
			for (int i = 4; i >= 1; i--) {
				g.setColor(new Color(80, 255, 110, 22));
				g.fill(new RoundRectangle2D.Double(ex - i, 34 - i, 12 + 2 * i, 13 + 2 * i, 3 + i, 3 + i));
			}
			g.setPaint(new GradientPaint(0, 34, new Color(170, 255, 170), 0, 47, new Color(50, 215, 85)));
			g.fill(new Rectangle2D.Double(ex, 34, 12, 13));
			g.setColor(new Color(240, 255, 240));
			g.fill(new Rectangle2D.Double(ex + 1.5, 35.5, 3.5, 3.5));
		}

		// Speaker grille
		RoundRectangle2D grille = new RoundRectangle2D.Double(36, 59, 24, 9, 3, 3);
		g.setColor(new Color(40, 46, 58));
		g.fill(grille);
		g.setColor(OUTLINE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(grille);
		g.setColor(new Color(150, 158, 170));
		for (int i = 0; i < 5; i++) {
			g.fill(new Rectangle2D.Double(39.2 + i * 4.2, 61, 1.4, 5));
		}
	}

	static void box(Graphics2D g, double x, double y, double w, double h, double arc, Color top, Color bottom) {
		RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, h, arc, arc);
		g.setPaint(new GradientPaint(0, (float) y, top, 0, (float) (y + h), bottom));
		g.fill(r);
		g.setColor(OUTLINE);
		g.setStroke(new BasicStroke(2f));
		g.draw(r);
	}

	/** The gear and lightning bolt from voltworks/tools/GenIcon.java, on its original 100-unit grid (centre 50,46). */
	static void voltworksMark(Graphics2D g) {
		Area gear = new Area(new Ellipse2D.Double(22, 18, 56, 56));
		for (int i = 0; i < 10; i++) {
			AffineTransform t = AffineTransform.getRotateInstance(Math.toRadians(i * 36), 50, 46);
			gear.add(new Area(t.createTransformedShape(new Rectangle2D.Double(45, 12, 10, 10))));
		}
		gear.subtract(new Area(new Ellipse2D.Double(36, 32, 28, 28)));
		g.setPaint(new GradientPaint(0, 14, new Color(170, 180, 195), 0, 78, new Color(110, 120, 135)));
		g.fill(gear);
		g.setColor(OUTLINE);
		g.setStroke(new BasicStroke(3f));
		g.draw(gear);

		Path2D bolt = new Path2D.Double();
		bolt.moveTo(57, 10);
		bolt.lineTo(31, 50);
		bolt.lineTo(48, 50);
		bolt.lineTo(40, 82);
		bolt.lineTo(70, 38);
		bolt.lineTo(52, 38);
		bolt.closePath();
		g.setPaint(new GradientPaint(40, 10, new Color(255, 240, 120), 55, 80, new Color(250, 170, 20)));
		g.fill(bolt);
		g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(60, 35, 0));
		g.draw(bolt);
	}

	static BufferedImage scale(BufferedImage src, int size) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, size, size, null);
		g.dispose();
		return out;
	}

	static void write(BufferedImage img, File f) throws Exception {
		f.getParentFile().mkdirs();
		ImageIO.write(img, "png", f);
	}
}
