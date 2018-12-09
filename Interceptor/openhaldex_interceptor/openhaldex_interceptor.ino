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
 *  Master frames are read into operating_mode and false_slip globals
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
#include "Timer.h"

#define ARRAY_LEN(array)    ((size_t)(sizeof(array) / sizeof(array[0])))

//Leave defined if you use native port, comment if using programming port
//#define Serial SerialUSB

#define HALDEX_ID       0x2C0
#define BRAKES1_ID      0x1A0
#define BRAKES3_ID      0x4A0
#define MOTOR1_ID       0x280
#define MOTOR3_ID       0x380
#define MOTOR6_ID       0x488
#define MASTER_ID       0x7ff

#define STOCK_LOCKING	0x0
#define NO_LOCKING		0x1
#define FULL_LOCKING    0x2
#define CUSTOM_LOCKING	0x3

#define CAN0_DEBUG      0
#define CAN1_DEBUG      0

static uint8_t operating_mode = 0;
static uint32_t false_slip = 0;
static const uint8_t motor1_lock_data[] = { 0x0, 0xf0, 0x20, 0x4e, 0xf0, 0xf0, 0x20, 0xf0 };

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
	if(incoming->id == MASTER_ID)
	{
		operating_mode = incoming->data.bytes[0];
        if(operating_mode == CUSTOM_LOCKING)
        {
            false_slip = (incoming->data.bytes[1] << 24) + (incoming->data.bytes[1] << 8);
        }
        else
        {
            false_slip = (0x1 << 24) + (0x1 << 8);
        }
	}
	else
	{
        if(operating_mode == NO_LOCKING && incoming->id == MOTOR1_ID)
        {
			incoming->data.bytes[3] = 0;
        }
		else if(operating_mode != STOCK_LOCKING)
		{
			get_lock_data(incoming);
		}

		Can0.sendFrame(*incoming);
	}
#if CAN1_DEBUG
	if(incoming->id == MOTOR1_ID)
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

void setup()
{    
    Serial.begin(115200);

    Can0.begin(CAN_BPS_500K);
    Can1.begin(CAN_BPS_500K);
    
    Can0.setRXFilter(0, HALDEX_ID, MASTER_ID, false);
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
}

void loop()
{
	/*
     * Do nothing, we're purely interrupt driven.
     */
}