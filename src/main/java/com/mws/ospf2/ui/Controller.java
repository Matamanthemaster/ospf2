package com.mws.ospf2.ui;

import com.mws.ospf2.Config;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class Controller {
    //@FXML private Label lblDispRID;
    @FXML private TextField txtRID;

    @FXML
    protected void initialize()
    {
        txtRID.setText(Config.thisNode.rID.toString());
        System.out.println(this + ".initialize()");
    }

    @FXML
    protected void TxtRIDKeyPressed(KeyEvent ev)
    {
        System.out.println(this + ".TxtRIDKeyPressed()");
        if (ev.getCode() == KeyCode.ENTER)
        {
            System.out.println("ENTER");
        }
    }
}
