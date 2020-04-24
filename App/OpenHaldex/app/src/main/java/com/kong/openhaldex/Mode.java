package com.kong.openhaldex;

import java.io.Serializable;
import java.util.ArrayList;

public class Mode implements Serializable {
    String name;
    ArrayList<LockPoint> lockPoints = new ArrayList<LockPoint>();
    boolean editable;

    public Mode(){ }

    public Mode(String _name, ArrayList<LockPoint> _lockPoints, boolean _editable){
        name = _name;
        lockPoints = _lockPoints;
        editable = _editable;
    }
}
