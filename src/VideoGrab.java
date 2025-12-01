import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.opencv.core.Core;

import java.io.File;
import java.util.List;

public class VideoGrab extends Application {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // 1. Load the FXML
            // Make sure "VideoGrab.fxml" matches the file name in your tree exactly
            FXMLLoader loader = new FXMLLoader(getClass().getResource("VideoGrab.fxml"));
            BorderPane rootElement = (BorderPane) loader.load();

            Scene scene = new Scene(rootElement, 1000, 600);
            primaryStage.setTitle("VidéoScramble");
            primaryStage.setScene(scene);

            // 2. Get the Controller
            // Ensure your FXML file has fx:controller="VideoGrabController"
            VideoGrabController controller = loader.getController();

            // 3. Retrieve the argument from Run Configuration
            Parameters params = getParameters();
            List<String> args = params.getRaw(); // This gets ["src/videoTest.m4v"]

            // Default values
            String videoPath = "video.mp4";
            String outputPath = "output.avi";
            long key = 12345;
            boolean encrypt = true;

            // Check if we have the file path argument
            if (!args.isEmpty()) {
                videoPath = args.get(0); // Takes "src/videoTest.m4v"
            }

            // Validate the file exists to avoid crashes
            File f = new File(videoPath);
            if(!f.exists()) {
                System.err.println("WARNING: File not found at: " + f.getAbsolutePath());
            }

            // 4. Pass data to Controller
            controller.initData(videoPath, outputPath, key, encrypt);

            primaryStage.show();

            // Handle close
            primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                public void handle(WindowEvent we) {
                    controller.setClosed();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}