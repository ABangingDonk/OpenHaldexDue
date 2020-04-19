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
 * OpenHaldex master:
 *  Sends haldex CAN frames to the app
 *  Sends mode data to the interceptor 
 *
 * Could have FISblocks integrated provided it doesn't take
 * too much processor time.
 */

#include <mcp_can.h>
#include <SPI.h>
#include "Timer.h"

#define HALDEX_ID       0x2C0
#define MASTER_ID       0x7ff

#define STOCK_LOCKING	0x0
#define NO_LOCKING		0x1
#define FULL_LOCKING    0x2
#define CUSTOM_LOCKING	0x3

// the cs pin of the version after v1.1 is default to D9
// v0.9b and v1.0 is default D10
#define SPI_CS_PIN      10

MCP_CAN CAN(SPI_CS_PIN);
static uint8_t haldex_engagement = 0;
static uint8_t haldex_status = 0;
Timer t;
//                              mode, custom slip
static uint8_t out_frame_data[] = {0, 0};


// Pass mode data from the app over to the interceptor
static void send_master_data()
{    
    CAN.sendMsgBuf(MASTER_ID, 0, 2, out_frame_data);
}

// Send data about the haldex to the app
static void send_app_data()
{
    Serial.write(0xff); // Start of frame marker
    Serial.write(haldex_status);
    Serial.write(haldex_engagement);
}

void setup()
{
    Serial.begin(115200);
    
    /* We only care about data from the haldex,
     * set up a filter so that frames with other
     * IDs get dropped so that the poor little
     * UNO doesn't get swamped. */
    CAN.init_Mask(0, 0, 0x3ff); // Bits to apply the filter to (all 1)
	CAN.init_Mask(1, 0, 0x3ff); // Bits to apply the filter to (all 1)
    CAN.init_Filt(0, 0, HALDEX_ID); // Ignore anything that doesn't match HALDEX_ID

    while (CAN_OK != CAN.begin(CAN_500KBPS))
    {
        delay(100);
    }
    
    t.every(2000, send_master_data, (void*)0); // Send data to the interceptor every 500ms
    t.every(100, send_app_data, (void*)0);    // Send data to the app every 100ms
}

void loop()
{
    t.update(); // Update the timer
    
    /* If we've got 3 or more bytes in the serial buffer then
     * we have at least one frame to process.
     * Loop through all at once. The app only sends when it
     * receives a frame so we shouldn't be getting stuck here
     * indefinately. */
    if(Serial.available() > 2)
    {
        uint8_t mode;
        uint8_t lock;
        
        /* If we see the start marker, the next two bytes should
         * be mode and lock data. */
        if(Serial.read() == 0xff)
        {
            mode = Serial.read();
            lock = Serial.read();
            
            if(mode <= CUSTOM_LOCKING) // Don't change to an invalid mode
            {
                out_frame_data[0] = mode;
                out_frame_data[1] = lock;
            }
            else // Invalid mode, go to stock mode.
            {
                out_frame_data[0] = STOCK_LOCKING;
            }
        }
    }
	
    // If there's a CAN message waiting for us
	if(CAN.checkReceive() == CAN_MSGAVAIL)
	{
		uint8_t len; // UNUSED
		unsigned long id;
		uint8_t buf[8];
		
		CAN.readMsgBufID(&id, &len, buf);
		
        /* We should only be getting messages from the haldex
         * but double check, just in case. */
		if(id == HALDEX_ID)
		{
			haldex_status = buf[0];
            /* We use 0xff as a frame marker; clamp to 0xfe so
             * that we don't screw the app over. */
			if(buf[1] == 0xff)
			{
				haldex_engagement = 0xfe;
			}
			else
			{
				haldex_engagement = buf[1];
			}
		}
	}
}