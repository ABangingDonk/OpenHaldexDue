package com.kong.openhaldex;

import java.io.Serializable;

public class LockPoint implements Serializable {
    int speed;
    int lock;
    int intensity;

    public LockPoint() {}

    public LockPoint(int _speed, int _lock, int _intensity){
        speed = _speed;
        lock = _lock;
        intensity = _intensity;
    }
}
