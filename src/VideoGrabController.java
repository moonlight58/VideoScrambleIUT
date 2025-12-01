import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;

/**
 * Controller for VidéoScramble.
 * Reads a video, scrambles/unscrambles lines based on a key, displays both, and saves output.
 */
public class VideoGrabController {

    @FXML private Button button;
    @FXML private ImageView originalFrame;
    @FXML private ImageView processedFrame;
    @FXML private Label keyLabel;
    @FXML private TextField keyTextField;

    private ScheduledExecutorService timer;
    private VideoCapture capture = new VideoCapture();
    private VideoWriter writer = null;

    private boolean isActive = false;

    // Configuration variables (Defaults, can be overwritten by CLI)
    private String inputPath = "video.mp4";
    private String outputPath = "output.avi";
    private long key = 4;
    private boolean encryptMode = true; // true = encrypt, false = decrypt

    /**
     * Receives command line arguments from Main
     */
    public void initData(String input, String output, long key, boolean mode) {
        this.inputPath = input;
        this.outputPath = output;
        this.key = key;
        this.encryptMode = mode;

        // Initialize text field with current key
        if (keyTextField != null) {
            keyTextField.setText(String.valueOf(key));
        }

        updateKeyLabel();
    }

    /**
     * Called when user modifies the key in the text field
     */
    @FXML
    protected void onKeyChanged(ActionEvent event) {
        try {
            long newKey = Long.parseLong(keyTextField.getText().trim());
            this.key = newKey;
            updateKeyLabel();

            // If processing is active, the new key will be used for the next frame automatically
            System.out.println("Key updated to: " + newKey);
        } catch (NumberFormatException e) {
            System.err.println("Invalid key format. Please enter a valid number.");
            // Restore previous valid key in text field
            keyTextField.setText(String.valueOf(this.key));
        }
    }

    private void updateKeyLabel() {
        String modeStr = encryptMode ? "Encryption" : "Decryption";
        this.keyLabel.setText(String.format("Key: %d | Mode: %s | Out: %s", key, modeStr, outputPath));
    }

    @FXML
    protected void startProcess(ActionEvent event) {
        if (!this.isActive) {
            // Open the video file instead of camera ID
            this.capture.open(inputPath);

            if (this.capture.isOpened()) {
                this.isActive = true;

                Runnable frameGrabber = new Runnable() {
                    @Override
                    public void run() {
                        Mat frame = grabFrame();

                        if (!frame.empty()) {
                            // 1. Initialize VideoWriter once if not already opened
                            if (writer == null) {
                                initVideoWriter(frame);
                            }

                            // 2. Display Original
                            Image imageOriginal = mat2Image(frame);
                            updateImageView(originalFrame, imageOriginal);

                            // 3. Process (Scramble or Descramble) - uses current key value
                            Mat processedMat = processRows(frame, key, encryptMode);

                            // 4. Display Processed
                            Image imageProcessed = mat2Image(processedMat);
                            updateImageView(processedFrame, imageProcessed);

                            // 5. Write to Disk
                            if (writer != null && writer.isOpened()) {
                                writer.write(processedMat);
                            }
                        } else {
                            // End of video reached
                            System.out.println("End of video stream.");
                            stopAcquisition();
                        }
                    }
                };

                this.timer = Executors.newSingleThreadScheduledExecutor();
                // 33ms is approx 30 FPS
                this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

                this.button.setText("Stop Processing");
            } else {
                System.err.println("Impossible to open video file: " + inputPath);
            }
        } else {
            this.isActive = false;
            this.button.setText("Start Processing");
            this.stopAcquisition();
        }
    }

    /**
     * Core logic: Scrambles or Unscrambles image rows based on the Key.
     */
    private Mat processRows(Mat input, long key, boolean isEncrypting) {
        Mat output = new Mat(input.size(), input.type());
        int height = input.height();

        // Create a deterministic map based on the key
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < height; i++) rows.add(i);
        Collections.shuffle(rows, new Random(key));

        for (int i = 0; i < height; i++) {
            int sourceIndex = i;
            int targetIndex = rows.get(i);

            if (isEncrypting) {
                // Encrypt: Move Row i -> Row map[i]
                input.row(sourceIndex).copyTo(output.row(targetIndex));
            } else {
                // Decrypt: Move Row map[i] -> Row i
                input.row(targetIndex).copyTo(output.row(sourceIndex));
            }
        }
        return output;
    }

    private void initVideoWriter(Mat frame) {
        int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G'); // MJPG codec
        // double fps = capture.get(Videoio.CAP_PROP_FPS); // Could try to get FPS from source
        writer = new VideoWriter(outputPath, fourcc, 30.0, frame.size(), true);
        if(!writer.isOpened()) {
            System.err.println("Could not open VideoWriter for " + outputPath);
        }
    }

    private Mat grabFrame() {
        Mat frame = new Mat();
        if (this.capture.isOpened()) {
            try {
                this.capture.read(frame);
            } catch (Exception e) {
                System.err.println("Exception during image read: " + e);
            }
        }
        return frame;
    }

    private void stopAcquisition() {
        if (this.timer != null && !this.timer.isShutdown()) {
            try {
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                System.err.println("Exception stopping timer: " + e);
            }
        }
        if (this.capture.isOpened()) {
            this.capture.release();
        }
        if (this.writer != null && this.writer.isOpened()) {
            this.writer.release();
            System.out.println("Video saved to " + outputPath);
            writer = null; // Reset for next run
        }
        // Update UI on FX Thread
        Platform.runLater(() -> button.setText("Start Processing"));
        this.isActive = false;
    }

    private void updateImageView(ImageView view, Image image) {
        onFXThread(view.imageProperty(), image);
    }

    public void setClosed() {
        this.stopAcquisition();
    }

    public static Image mat2Image(Mat frame) {
        try {
            return SwingFXUtils.toFXImage(matToBufferedImage(frame), null);
        } catch (Exception e) {
            System.err.println("Cannot convert the Mat object: " + e);
            return null;
        }
    }

    private static BufferedImage matToBufferedImage(Mat original) {
        BufferedImage image = null;
        int width = original.width(), height = original.height(), channels = original.channels();
        byte[] sourcePixels = new byte[width * height * channels];
        original.get(0, 0, sourcePixels);

        if (original.channels() > 1) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        } else {
            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        }
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);

        return image;
    }

    public static <T> void onFXThread(final ObjectProperty<T> property, final T value) {
        Platform.runLater(() -> property.set(value));
    }
}