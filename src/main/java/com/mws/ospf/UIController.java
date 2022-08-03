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

public class UIController {
    @FXML private Label lblOutStatus;
    //@FXML private Label lblDispRID;
    @FXML private TextField txtRID;
    @FXML private TabPane tbpNeighbours;
    private Timeline timerUpdateGUI = new Timeline(
            new KeyFrame(Duration.seconds(1),
                    event -> UpdateTimerTick(event)));


    @FXML
    protected void initialize()
    {
        txtRID.setText(String.valueOf(Config.thisNode.GetRID()));
        tbpNeighbours.getTabs().clear();
        timerUpdateGUI.setCycleCount(Timeline.INDEFINITE);
        timerUpdateGUI.play();
    }

    @FXML
    protected void TxtRIDKeyPressed(KeyEvent ev)
    {
        //On Key enter, update RID
        if (ev.getCode() == KeyCode.ENTER) {
            try {
                Config.thisNode.SetRID(new IPAddressString(txtRID.getText()));
                Config.WriteConfig();
            } catch (IllegalArgumentException ex) {
                lblOutStatus.setText("RID was invalid, reverted to default value");
                txtRID.setText(Config.thisNode.GetRID().toString());
            }
        }
    }

    private void UpdateTimerTick(ActionEvent ev) {
        for (NeighbourNode n : Config.neighboursTable) {
            if (!tbpNeighbours.getTabs().contains(n.tab)) {
                AddGUINeighbour(n);
            }

            VBox vbTabPropValues = (VBox) ((HBox) n.tab.getContent()).getChildren().get(1);
            ((Label) vbTabPropValues.getChildren().get(0)).setText(n.GetRID().toString());
            ((Label) vbTabPropValues.getChildren().get(1)).setText(n.ipAddress.toPrefixLengthString());
            ((Label) vbTabPropValues.getChildren().get(2)).setText(n.GetState().toString());
            ((Label) vbTabPropValues.getChildren().get(3)).setText(n.GetKnownNeighboursString());
        }


    }
    private void AddGUINeighbour(NeighbourNode n) {
        HBox hbTabRoot = new HBox();
        VBox vbTabPropNames = new VBox();
        VBox vbTabPropValues = new VBox();
        hbTabRoot.getChildren().add(0, vbTabPropNames);
        hbTabRoot.getChildren().add(1, vbTabPropValues);

        vbTabPropNames.getChildren().add(0, new Label("RID: "));
        vbTabPropValues.getChildren().add(0, new Label(n.GetRID().toString()));

        vbTabPropNames.getChildren().add(1, new Label("IPv4: "));
        vbTabPropValues.getChildren().add(1, new Label(n.ipAddress.toPrefixLengthString()));

        vbTabPropNames.getChildren().add(2, new Label("State: "));
        vbTabPropValues.getChildren().add(2, new Label(n.GetState().toString()));

        vbTabPropNames.getChildren().add(3, new Label("Known Neighbours: "));
        vbTabPropValues.getChildren().add(3, new Label(n.GetKnownNeighboursString()));

        n.tab = new Tab();
        n.tab.setText(n.GetRID().toString());
        n.tab.setContent(hbTabRoot);

        tbpNeighbours.getTabs().add(n.tab);
    }

    static void OnClose(WindowEvent ev){
        System.exit(0);
    }
}
