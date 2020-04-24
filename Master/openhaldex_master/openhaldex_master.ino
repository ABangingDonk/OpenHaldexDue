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
#include <Timer.h>

#define HALDEX_ID                 0x2C0
#define INTERCEPTOR_INFO_ID       0x7fb
#define INTERCEPTOR_DATA_CTRL_ID  0x7fc
#define MASTER_MODE_ID            0x7fd
#define MASTER_DATA_CTRL_ID       0x7fe
#define MASTER_DATA_ID            0x7ff

#define MODE_STOCK          0x0
#define MODE_FWD            0x1
#define MODE_5050           0x2
#define MODE_CUSTOM         0x3

#define DATA_CTRL_CHECK_LOCKPOINTS  0
#define DATA_CTRL_CLEAR             1
#define DATA_CTRL_CHECK_MODE        2

#define APP_MSG_MODE        0
#define APP_MSG_STATUS      1
#define APP_MSG_CUSTOM_DATA 2
#define APP_MSG_CUSTOM_CTRL 3

#define TEST_MODE           0

#define ARRAY_LEN(array)    ((size_t)(sizeof(array) / sizeof(array[0])))

// the cs pin of the version after v1.1 is default to D9
// v0.9b and v1.0 is default D10
#define SPI_CS_PIN      10

MCP_CAN CAN(SPI_CS_PIN);
static uint8_t haldex_engagement = 0;
static uint8_t haldex_status = 0;
Timer t;
static uint8_t current_mode = MODE_STOCK;
static uint8_t target_lock = 0;
static uint8_t vehicle_speed = 0;
static uint8_t ped_threshold = 0;

#if TEST_MODE
static uint8_t test_speed = 0;
#endif


// Pass mode data from the app over to the interceptor
void send_mode(void* context)
{   
    uint8_t out_buf[2] = {
        current_mode, ped_threshold
    };
    CAN.sendMsgBuf(MASTER_MODE_ID, 0, 2, out_buf);
    
    #if TEST_MODE
    uint8_t test_data[8] = {
        0,0,0,test_speed++,0,test_speed & 8,0,0
    };
    CAN.sendMsgBuf(0x280, 0, 8, test_data);
    CAN.sendMsgBuf(0x288, 0, 8, test_data);
    #endif
}

// Send data about the haldex to the app
void send_app_data(void* context)
{
    Serial.write(0xff); // Frame start marker
    Serial.write(APP_MSG_STATUS);
    Serial.write(haldex_status);
    Serial.write(haldex_engagement);
    Serial.write(target_lock);
    Serial.write(vehicle_speed);
}

void setup()
{
    Serial.begin(115200);
    
    /* We only care about data from the haldex and interceptor,
     * set up filters so that frames with other IDs get dropped 
     * so that the poor little UNO doesn't get swamped. */
    CAN.init_Mask(0, 0, 0x3ff); // Bits to apply the filter to (all 1)
    CAN.init_Mask(1, 0, 0x3ff); // Bits to apply the filter to (all 1)
    CAN.init_Filt(0, 0, HALDEX_ID);
    CAN.init_Filt(1, 0, INTERCEPTOR_DATA_CTRL_ID);

    while (CAN_OK != CAN.begin(CAN_500KBPS))
    {
        delay(100);
    }
    
    t.every(500, send_mode, (void*) 0); // Send data to the interceptor every 1s
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
    while(Serial.available() > 5)
    {        
        /* If we see the start marker, the next byte will be message ID */
        if(Serial.read() == 0xff)
        {
            uint8_t message_type = Serial.read();
            
            switch (message_type)
            {
                case APP_MSG_MODE:
                {
                    uint8_t mode = Serial.read();
                    ped_threshold = Serial.read();
                    if (mode <= MODE_CUSTOM)
                    {
                        current_mode = mode;
                    }
                    else
                    {
                        // Invalid mode
                        current_mode = MODE_STOCK;
                    }
                    break;
                }
                case APP_MSG_CUSTOM_DATA:
                {
                    uint8_t out_frame[4];
                    out_frame[0] = Serial.read();
                    out_frame[1] = Serial.read();
                    out_frame[2] = Serial.read();
                    out_frame[3] = Serial.read();
                    CAN.sendMsgBuf(MASTER_DATA_ID, 0, ARRAY_LEN(out_frame), out_frame);
                    break;
                }
                case APP_MSG_CUSTOM_CTRL:
                {
                    uint8_t ctrl_option = Serial.read();
                    CAN.sendMsgBuf(MASTER_DATA_CTRL_ID, 0, 1, &ctrl_option);
                }
                default:
                {
                    // Error handling?
                }
            }
        }
    }
    
    // If there's a CAN message waiting for us
    if(CAN.checkReceive() == CAN_MSGAVAIL)
    {
        uint8_t len;
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
        else if (id == INTERCEPTOR_DATA_CTRL_ID)
        {
            Serial.write(0xff); // Frame start marker
            Serial.write(APP_MSG_CUSTOM_CTRL);
            for (int i = 0; i < len; i++)
            {
                Serial.write(buf[i]);                
            }
        }
        else if (id == INTERCEPTOR_INFO_ID)
        {
            target_lock = buf[0];
            vehicle_speed = buf[1];
        }
    }
    
    
}