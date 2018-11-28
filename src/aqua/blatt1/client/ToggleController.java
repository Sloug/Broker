package aqua.blatt1.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ToggleController implements ActionListener {
    private final Component parent;
    private final TankModel tankModel;
    private final String fishId;


    public ToggleController(Component parent, TankModel tankModel, String fishId) {
        this.parent = parent;
        this.tankModel = tankModel;
        this.fishId = fishId;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        tankModel.locateFishGloballyHomeAgent(fishId);
    }
}