package com.kong.openhaldex;

import java.io.Serializable;

public class LockPoint implements Serializable {
    float speed;
    float lock;
    float intensity;

    public LockPoint() {}

    public LockPoint(float _speed, float _lock, float _intensity){
        speed = _speed;
        lock = _lock;
        intensity = _intensity;
    }
}
