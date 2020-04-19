/*
 * MIT License
 * 
 * Copyright (c) 2018 ABangingDonk
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * OpenHaldex interceptor:
 *  Forwards all CAN frames (except those from the master) to the haldex
 *  Master frames are read into persistent_config.mode and false_slip globals
 *
 * Some experimentation to be done with regards to false_slip and BRAKES1
 * frames... I've seen a maximum haldex engagement of 0xc6, I'm sure there's
 * more to be had here.
 *
 * At the very least, we can keep the haldex enaged through braking by editing
 * the brake pedal signal sent in BRAKES1.
 */

#include "variant.h"
#include <due_can.h>
#include <Timer.h>
#include "DueFlashStorage.h"
#include <stdio.h>

#define ARRAY_LEN(array)    ((size_t)(sizeof(array) / sizeof(array[0])))

//Leave defined if you use native port, comment if using programming port
//#define Serial SerialUSB

#define HALDEX_ID                 0x2C0
#define BRAKES1_ID                0x1A0
#define BRAKES3_ID                0x4A0
#define MOTOR1_ID                 0x280
#define MOTOR3_ID                 0x380
#define MOTOR6_ID                 0x488
#define INTERCEPTOR_DATA_CTRL_ID  0x7fc
#define MASTER_MODE_ID            0x7fd
#define MASTER_DATA_CTRL_ID       0x7fe
#define MASTER_DATA_ID            0x7ff

#define MODE_STOCK   0x0
#define MODE_FWD     0x1
#define MODE_5050    0x2
#define MODE_CUSTOM  0x3

#define DATA_CTRL_CHECK_LOCKPOINTS  0
#define DATA_CTRL_CLEAR             1
#define DATA_CTRL_CHECK_MODE        2

#define CAN0_DEBUG      1
#define CAN1_DEBUG      1
#define STATE_DEBUG     0

#define NUM_LOCK_POINTS 10

typedef struct lockpoint{
    uint8_t speed;
    uint8_t lock;
    uint8_t intensity;
}lockpoint;

typedef struct PersistentConfig{
    uint8_t mode;
    lockpoint lockpoints[NUM_LOCK_POINTS];
}PersistentConfig;

static PersistentConfig persistent_config;
static uint32_t false_slip = (0x1 << 24) + (0x1 << 8);
static const uint8_t motor1_lock_data[] = { 0x0, 0xf0, 0x20, 0x4e, 0xf0, 0xf0, 0x20, 0xf0 };
DueFlashStorage dueFlashStorage;
static uint16_t lockpoint_rx = 0;
Timer t;


static void get_lock_data(CAN_FRAME *frame)
{   
    switch(frame->id)
    {
        case MOTOR1_ID:
            memcpy(frame->data.bytes, motor1_lock_data, ARRAY_LEN(motor1_lock_data));
            break;
        case MOTOR3_ID:
            frame->data.bytes[2] = 0xfa;
            frame->data.bytes[7] = 0xfe;
            break;
        case MOTOR6_ID:
            frame->data.bytes[1] = 0xfe;
            frame->data.bytes[2] = 0xfe;
            break;
        case BRAKES3_ID:
            frame->data.high = (0xa << 24) + (0xa << 8);
            frame->data.low = frame->data.high + false_slip;
            break;
        case BRAKES1_ID:
            //frame->data.bytes[1] &= ~0x8;
            frame->data.bytes[2] = 0x0;
            frame->data.bytes[3] = 0xa;
            break;
    }
}

void update_eeprom(void* context)
{
    byte *old_data = dueFlashStorage.readAddress(4);
    // If config has changed then we should update it
    if (memcmp(old_data, &persistent_config, sizeof(persistent_config)))
    {
        byte new_data[sizeof(persistent_config)];
        memcpy(new_data, &persistent_config, sizeof(persistent_config));
        dueFlashStorage.write(4, new_data, sizeof(new_data));
        Serial.println("persistent_config changed, saving to flash");
    }
    #if STATE_DEBUG
    Serial.print("Mode: ");
    Serial.println(persistent_config.mode);
    
    #endif
}

void haldex_callback(CAN_FRAME *incoming)
{
    Can1.sendFrame(*incoming, 7);

    #if CAN0_DEBUG
    if(incoming->data.bytes[0])
    {
        Serial.print("ID: 0x");
        Serial.print(incoming->id,HEX);
        Serial.print(" DATA: ");
        for(int i = 0; i < incoming->length; i++)
        {
            Serial.print(incoming->data.bytes[i],HEX);
            Serial.print(" ");
        }
        Serial.println();
    }
    #endif
}

void can1_rx_callback(CAN_FRAME *incoming)
{
    uint32_t id = incoming->id;
    
    if(id == MASTER_MODE_ID)
    {
        // We'll get this periodically from the master to tell us which mode we should be in.
        persistent_config.mode = incoming->data.bytes[0];
    }
    else if (id == MASTER_DATA_ID)
    {
        // Receiving data for custom lock mode
        uint8_t lockpoint_index = incoming->data.bytes[0];
        
        // Make sure that the index we were given is within range
        if (lockpoint_index < NUM_LOCK_POINTS)
        {
            // Read lockpoint properties out of the CAN packet
            persistent_config.lockpoints[lockpoint_index].speed = incoming->data.bytes[1];
            persistent_config.lockpoints[lockpoint_index].lock = incoming->data.bytes[2];
            persistent_config.lockpoints[lockpoint_index].intensity = incoming->data.bytes[3];
            
            // Set the bit for this lockpoint to indicate we've received it
            lockpoint_rx |= (1 << lockpoint_index);
        }
    }
    else if (id == MASTER_DATA_CTRL_ID)
    {
        // Transfer control for custom lock mode data
        CAN_FRAME rsp;
        rsp.id = INTERCEPTOR_DATA_CTRL_ID;
        rsp.extended = false;
        switch (incoming->data.bytes[0])
        {
            case DATA_CTRL_CHECK_LOCKPOINTS:
            {
                // Send the bitfield value to say which lockpoints we've received
                rsp.data.bytes[0] = DATA_CTRL_CHECK_LOCKPOINTS;
                rsp.data.bytes[1] = lockpoint_rx & 0xff;
                rsp.data.bytes[2] = (lockpoint_rx << 8) & 0xff;
                rsp.length = 3;
                Can1.sendFrame(rsp, 7);
                #if CAN1_DEBUG
                    if(1)//incoming->id == MOTOR1_ID)
                    {
                        Serial.print("TX: ID: 0x");
                        Serial.print(rsp.id,HEX);
                        Serial.print(" DATA[");
                        Serial.print(rsp.length);
                        Serial.print("] : ");
                        for(int i = 0; i < rsp.length; i++)
                        {
                            Serial.print(rsp.data.bytes[i],HEX);
                            Serial.print(" ");
                        }
                        Serial.println();
                    }
                #endif
                break;
            }
            case DATA_CTRL_CLEAR:
            {
                // We've been told to clear - so clear the lockpoints and bitfield
                lockpoint_rx = 0;
                memset(persistent_config.lockpoints, 0, sizeof(persistent_config.lockpoints));
                break;
            }
            case DATA_CTRL_CHECK_MODE:
            {
                // Send the bitfield value to say which lockpoints we've received
                rsp.data.bytes[0] = DATA_CTRL_CHECK_MODE;
                rsp.data.bytes[1] = persistent_config.mode;
                rsp.length = 2;
                Can1.sendFrame(rsp, 7);
                #if CAN1_DEBUG
                    if(1)//incoming->id == MOTOR1_ID)
                    {
                        Serial.print("TX: ID: 0x");
                        Serial.print(rsp.id,HEX);
                        Serial.print(" DATA[");
                        Serial.print(rsp.length);
                        Serial.print("] : ");
                        for(int i = 0; i < rsp.length; i++)
                        {
                            Serial.print(rsp.data.bytes[i],HEX);
                            Serial.print(" ");
                        }
                        Serial.println();
                    }
                #endif
                break;
            }
            default:
            {
                // Error handling?
                break;
            }
        }
    }
    else
    {
        // Anything which has come from the car.. i.e. everything except Haldex and Master.
        if(persistent_config.mode == MODE_FWD && incoming->id == MOTOR1_ID)
        {
            incoming->data.bytes[3] = 0;
        }
        else if(persistent_config.mode == MODE_5050)
        {
            get_lock_data(incoming);
        }

        Can0.sendFrame(*incoming);
    }
#if CAN1_DEBUG
    if(1)//incoming->id == MOTOR1_ID)
    {
        Serial.print("ID: 0x");
        Serial.print(incoming->id,HEX);
        Serial.print(" DATA: ");
        for(int i = 0; i < incoming->length; i++)
        {
            Serial.print(incoming->data.bytes[i],HEX);
            Serial.print(" ");
        }
        Serial.println();
    }
    CAN_FRAME rsp;
    rsp.id = HALDEX_ID;
    rsp.extended = false;
    rsp.length = 2;
    rsp.data.bytes[0] = 0x0;
    rsp.data.bytes[1] = 0x85;
    haldex_callback(&rsp);
#endif
}

void setup()
{    
    Serial.begin(115200);

    Can0.begin(CAN_BPS_500K);
    Can1.begin(CAN_BPS_500K);
    
    Can0.setRXFilter(0, HALDEX_ID, 0x7ff, false);
    Can0.setCallback(0, haldex_callback);
    Can0.mailbox_set_mode(1,3);
    Can0.mailbox_set_mode(2,3);
    Can0.mailbox_set_mode(3,3);
    Can0.mailbox_set_mode(4,3);
    Can0.mailbox_set_mode(5,3);
    Can0.mailbox_set_mode(6,3);
    Can0.mailbox_set_mode(7,3);
    
    Can1.setRXFilter(0, 0, 0, false);
    Can1.setCallback(0, can1_rx_callback);
    Can1.setRXFilter(1, 0, 0, false);
    Can1.setCallback(1, can1_rx_callback);
    Can1.setRXFilter(2, 0, 0, false);
    Can1.setCallback(2, can1_rx_callback);
    Can1.setRXFilter(3, 0, 0, false);
    Can1.setCallback(3, can1_rx_callback);
    Can1.setRXFilter(4, 0, 0, false);
    Can1.setCallback(4, can1_rx_callback);
    Can1.setRXFilter(5, 0, 0, false);
    Can1.setCallback(5, can1_rx_callback);
    Can1.setRXFilter(6, 0, 0, false);
    Can1.setCallback(6, can1_rx_callback);
    Can1.mailbox_set_mode(7,3);
    
    Can0.disable_tx_repeat();
    Can1.disable_tx_repeat();
    
    if (dueFlashStorage.read(0) != 0xff)
    {
        Serial.println(dueFlashStorage.read(0));
        // We have saved config - so load it...
        byte *b = dueFlashStorage.readAddress(4);
        memcpy(&persistent_config, b, sizeof(persistent_config));
        #if STATE_DEBUG
        Serial.println("Found persistent config");
        Serial.print("Mode: ");
        Serial.println(persistent_config.mode);
        for (int i = 0; i < NUM_LOCK_POINTS; i++)
        {
            char s[256];
            sprintf(s, "Lockpoint[%d]:", i);
            Serial.println(s);
            sprintf(s, "speed=%d, lock=%d, intensity=%d", persistent_config.lockpoints[i].speed, persistent_config.lockpoints[i].lock, persistent_config.lockpoints[i].intensity);
            Serial.println(s);
        }
        #endif
    }
    else
    {
        // We didn't have a saved config.. so write 0 to address 0 so that we
        // don't come through this again.
        dueFlashStorage.write(0, 0x00);
        
        memset(&persistent_config, 0, sizeof(persistent_config));
        
        // And put a blank copy of config so that we are less likely to load
        // f's and get into some weird state....
        update_eeprom((void*)0);
    }
    
    // Update EEPROM every 2 seconds (will only write if contents needs updating)
    t.every(2000, update_eeprom, (void*)0);
}

void loop()
{
    /*
     * Do nothing, we're purely interrupt driven.
     */
     
    t.update(); // Update the timer
}