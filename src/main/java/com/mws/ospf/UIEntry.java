package com.mws.ospf;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**<p><h1>UI Entry Class</h1></p>
 * <p>Class is entry point to JavaFX GUI component of the artefact. Launcher class calls main if the artefact is to use
 * the GUI</p>
 */
class UIEntry extends Application {
    /**<p><h1>UI Entry Main</h1></p>
     * <p>Entrypoint of the GUI application, calls javafx method to create a javafx stage, and eventually call start</p>
     * @param args arguments to be passed
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**<p><h1>UI Entry Start</h1></p>
     * <p>JavaFX method to set up a scene within a stage, under programmer control. Sets up the JavaFx Stage, and the
     * scene (content) of the stage from a fxml file.</p>
     * @param primaryStage stage passed by JavaFX methods, derived from compositor
     * @throws IOException on loading fxml document from FXML Loader
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(UIEntry.class.getResource("controlUI.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        primaryStage.setTitle("[OSPFv" + Launcher.operationMode +
                "@" + Config.thisNode.hostname+ "]");
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(UIController::onClose);
    }
}
