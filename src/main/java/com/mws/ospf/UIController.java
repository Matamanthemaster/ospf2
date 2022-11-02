package com.mws.ospf;

import inet.ipaddr.IPAddressString;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/**<p><h1>JavaFX GUI Scene Controller</h1></p>
 * <p>Controller class for the GUI application in the artefact. Allows manipulation of the JavaFX application
 * programmatically by firing events specified in the FXML document. On event firings, specified methods are called,
 * able to manipulate other elements of the GUI application. Must be public for FXML reference.</p>
 */
public class UIController {
    //region OBJECT PROPERTIES
    @FXML private Label lblOutStatus;
    //@FXML private Label lblDispRID;
    @FXML private TextField txtRID;
    @FXML private TabPane tbpNeighbours;
    private final Timeline timerUpdateGUI = new Timeline(
            new KeyFrame(Duration.seconds(1),
                    this::updateTimerTick));
    //endregion OBJECT PROPERTIES

    //region STATIC METHODS
    /**<p><h1>Stage OnClose Event</h1></p>
     * <p>Static event triggered on window close. Set up as event in UIEntry.start method</p>
     * @param ev event parameters
     */
    static void onClose(WindowEvent ev){
        System.exit(0);
    }
    //endregion STATIC METHODS

    //region OBJECT METHODS
    /**<p><h1>UI Initialise</h1></p>
     * <p>Called on creation of the scene. Closest thing to an object constructor</p>
     */
    @FXML
    protected void initialize() {
        txtRID.setText(String.valueOf(Config.thisNode.getRID()));
        tbpNeighbours.getTabs().clear();
        timerUpdateGUI.setCycleCount(Timeline.INDEFINITE);
        timerUpdateGUI.play();
    }

    /**<p><h1>TxtRID Key Pressed Event</h1></p>
     * <p>Event fired on TxtRID Key Pressed. Updates this node's RID.</p>
     * @param ev Key event parameters
     */
    @FXML
    protected void txtRIDKeyPressed(KeyEvent ev) {
        //On Key enter, update RID
        if (ev.getCode() == KeyCode.ENTER) {
            try {
                Config.thisNode.setRID(new IPAddressString(txtRID.getText()));
                Config.writeConfig();
            } catch (IllegalArgumentException ex) {
                lblOutStatus.setText("RID was invalid, reverted to default value");
                txtRID.setText(Config.thisNode.getRID().toString());
            }
        }
    }

    /**<p><h1>Update Timer Tick</h1></p>
     * <p>Called when the update timeline fires, updating the UI elements every second</p>
     * @param ev event parameters
     */
    private void updateTimerTick(ActionEvent ev) {
        for (NeighbourNode n : Config.neighboursTable) {
            if (!tbpNeighbours.getTabs().contains(n.tab)) {
                addGUINeighbour(n);
            }

            VBox vbTabPropValues = (VBox) ((HBox) n.tab.getContent()).getChildren().get(1);
            ((Label) vbTabPropValues.getChildren().get(0)).setText(n.getRID().toString());
            ((Label) vbTabPropValues.getChildren().get(1)).setText(n.ipAddress.toPrefixLengthString());
            ((Label) vbTabPropValues.getChildren().get(2)).setText(n.getState().toString());
            ((Label) vbTabPropValues.getChildren().get(3)).setText(n.getKnownNeighboursString());
        }


    }

    /**<p><h1>Add Neighbour to GUI</h1></p>
     * <p>Takes a neighbour node and creates a tab, storing it in the tab for the neighbour node. Will create the visible
     * elements, and populate initial values</p>
     * @param n Neighbour node being added to the UI.
     */
    private void addGUINeighbour(NeighbourNode n) {
        HBox hbTabRoot = new HBox();
        VBox vbTabPropNames = new VBox();
        VBox vbTabPropValues = new VBox();
        hbTabRoot.getChildren().add(0, vbTabPropNames);
        hbTabRoot.getChildren().add(1, vbTabPropValues);

        vbTabPropNames.getChildren().add(0, new Label("RID: "));
        vbTabPropValues.getChildren().add(0, new Label(n.getRID().toString()));

        vbTabPropNames.getChildren().add(1, new Label("IPv4: "));
        vbTabPropValues.getChildren().add(1, new Label(n.ipAddress.toPrefixLengthString()));

        vbTabPropNames.getChildren().add(2, new Label("State: "));
        vbTabPropValues.getChildren().add(2, new Label(n.getState().toString()));

        vbTabPropNames.getChildren().add(3, new Label("Known Neighbours: "));
        vbTabPropValues.getChildren().add(3, new Label(n.getKnownNeighboursString()));

        n.tab = new Tab();
        n.tab.setText(n.getRID().toString());
        n.tab.setContent(hbTabRoot);

        tbpNeighbours.getTabs().add(n.tab);
    }
    //endregion OBJECT METHODS
}
