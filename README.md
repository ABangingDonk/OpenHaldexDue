
# OpenHaldex

## Brief
Gain control over the [Gen 1 Haldex](https://www.awdwiki.com/en/haldex/#haldex_generation_I__1998_2001 "Gen 1 Haldex") equipped to Quattro and 4Motion vehicles based on the Mk. 4 VW Golf platform (8N Audi TT, 8L Audi A3, 1J VW Golf, etc.)

Installation notes here are for a Mk1 Audi TT.

## App notes
### Features added in v2.0
- Custom modes (up to 10 data points)
- Interceptor will boot up in the same mode it was switched off in
- Added minimum pedal threshold option
- Improved overall system robustness

### Basic usage
- Comes with three pre-loaded modes which cannot be deleted or edited
 - Stock
 - FWD
 - 50/50
- Minimum pedal threshold can be used to prevent binding during tight low speed turns (such as when parking)
 - I recommend setting this to 10% or 15%

### Custom modes
- Custom modes that have been added can be edited via long press
- Vehicle speed will be used to lookup the lock target
 - If vehicle speed is between two lock points, the lock value will be interpolated
 - If vehicle speed is lower than that of the first lock point, the lock value of the first lock point will be used
 - If vehicle speed is greater than that of the last lock point, the lock value of the last lock point will be used
- **Lock points are expected to be ordered by speed (ascending.. i.e. lowest to highest). This is not enforced by software and I have no idea what chaos will ensue if not following this rule.**
- You can add as many custom modes as you like however they must be given unique names
- Custom modes will be lost if uninstalling the App

## Installation
### Shopping list:
- 1x [Arduino DUE R3 Board SAM3X8E](https://www.ebay.co.uk/itm/263235052149 "Arduino DUE R3 Board SAM3X8E") £12.49
- 1x [Wire Kit 22-23 AWG Single Solid Core](https://www.ebay.co.uk/itm/232901601951 "Wire Kit 22-23 AWG Single Solid Core") £4.22
- 1x [Arduino Uno R3](https://www.ebay.co.uk/itm/262200725666 "Arduino Uno R3") £4.95
- 1x [MCP2515 Can Bus Shield](https://www.ebay.co.uk/itm/183396672352 "MCP2515 Can Bus Shield") £6.09
- 1x [HC-05 Bluetooth Serial Module](https://www.ebay.co.uk/itm/251742351122 "HC-05 Bluetooth Serial Module") £5.95
- 1x [USB to TTL Serial Converter Adapter](https://www.ebay.co.uk/itm/322459046657 "USB to TTL Serial Converter Adapter") £2.69
Subtotal £36.39

Either:<br>
2x [SN65HVD230 CAN Bus Transceiver](https://www.ebay.co.uk/itm/283224837984 "SN65HVD230 CAN Bus Transceiver") £3.52<br>
OR<br>
1x [CAN shield for Arduino Due](https://copperhilltech.com/dual-can-bus-interface-for-arduino-due/ "CAN shield for Arduino Due") $36.95

Either:<br>
2x [VAG 8 pin connector plug set](https://www.ebay.co.uk/itm/183041653479 "VAG 8 pin connector plug set") £21.98 (if you are being fancy)<br>
OR<br>
1x [Dupont Wire Jumper Pin Header Connector](https://www.ebay.co.uk/itm/122090496971 "Dupont Wire Jumper Pin Header Connector") £7.19 (less fancy but has come in handy so many times over the years)

eBay links will die (some are dead already), substitute with similar items.

### Tools required
- Soldering iron, solder (you want the multi-core leaded stuff), helping hand
- Needle nose pliers
- Stanley knife
- Socket set (13mm to remove wiper blade arms)
- Multimeter (probably not required but a handy thing to have around)

### Interceptor (Due)
#### Prep
I need to give credit to [this site](https://copperhilltech.com/blog/app-note-testing-arduino-due-with-2-can-bus-breakout-boards/ "this site"). It was bloody useful. You could buy a shield from these guys (linked above) which includes CAN transceivers and means you can skip all of this prep work.

The Arduino Due is an awesome bit of kit but it requires CAN transceivers to make use of its two CAN ports. I wired mine together like this

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_transceivers_1.jpg?raw=true" width="50%"/>

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_transceivers_2.jpg?raw=true" width="50%"/>

The Due has only one regulated 3v3 output so I chose to wire the two transceivers together and bodge up a connector made out of a 4x1 and a 6x1. You could make life easier by using a 6x2 and connecting them both to the Due's 3v3 output.

For the other pins, CTX & CRX connect to the Due and CANH & CANL form one side of the CAN bridge we are building, we'll get back to this later. CTX & CRX connect to these pins of the Due

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/arduino-due-can-ports-pinout.jpeg?raw=true" width="50%"/>

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_interceptor_1.jpg?raw=true" width="50%"/>

You don't want to mix these up, one transceiver is CAN0 and the other is CAN1.

You want to have the CANH & CANL from one transceiver to have a male connector and the other one female, it will become clear why when we come to install.

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_transceivers_3.jpg?raw=true" width="50%"/>

You should end up with something that looks like this

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_interceptor_2.jpg?raw=true" width="50%"/>

Now is a good time to program the Due using the sketch in <tt>OpenHaldex/Interceptor/openhaldex_interceptor/</tt> see notes below for dependencies.

#### Install
Take out the spare wheel and toolkit to expose the wiring going down to the Haldex controller

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_boot_location.jpg?raw=true" width="50%"/>

Gently cut the fabric sheath and unravel a 10cm section or so. The wires we're interested in here are:

CAN high: solid black (narrow gauge)<br>
CAN low: solid white (narrow gauge)<br>
12V switched: black/grey<br>
Ground: solid brown

Expect the CAN lines to be a (loosely) twisted pair.

Splice some new wires into the switched 12V and ground cables in the loom

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_insall_boot_splice_1.jpg?raw=true" width="50%"/>

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_insall_boot_splice_2.jpg?raw=true" width="50%"/>

And chuck them into a male 2x1 connector

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_insall_boot_splice_3.jpg?raw=true" width="50%"/>

Now cut the CAN lines (solid black & solid white). If you did the prep work like I did above, give them 2x1 connectors so that CAN0 connects to the Haldex (the side towards the front of the car) and CAN1 connects to the rest of the bus (towards the back of the car).

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_insall_boot_splice_4.jpg?raw=true" width="50%" style="transform:rotate(180deg)"/>

### Master (Uno)
#### Prep
A little more simple than the interceptor, we just need to make two 2x1 connections to the Bluetooth module; one for power and the other for data. The data lines should be crossed i.e. TX on the Bluetooth module should connect to RX on the Uno's CAN shield and vice versa.

The Bluetooth module gets power from the Uno's 5v output (of course, you don't cross this one!)

The CAN shield has a terminating 120Ω resistor which we need to disable. To do this, we need to use a knife to cut the trace between the two pads next to the power switch.

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_master_1.jpg?raw=true" width="50%"/>

We need to do some coding for the Bluetooth module before we can use it. [This page](https://www.instructables.com/id/AT-command-mode-of-HC-05-Bluetooth-module/ "This page") says it all really... We need to set the name to OpenHaldex and the baudrate to 115200.

One day there will be some code in the master unit to configure the BT module automatically.. but for now use the USB-TTL converter, TeraTerm and the guide linked.

You also need to to program the Uno using the sketch in <tt>OpenHaldex/Master/openhaldex_master/</tt> see notes below for dependencies.

#### Install
**Ignore the extra brown/blue mains leads in these pictures, they are courtesy of wideband conversion (yes another shocking bodge)**

We need to get to the white connection box under the scuttle panel

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_master_2.jpg?raw=true" width="50%"/>

Open up the box by pushing back the clips and working the lid loose. I loathe doing this, it's a pain but it gets a bit easier after the first 10 times....

Once you've got that open, look for the white connector. We need to splice into pins 2 and 3. The pin numbering is moulded into the plastic connector, you can confirm using the stupidly subtle difference in wire colour (CAN high orange/black, CAN low orange/brown)

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_master_4.jpg?raw=true" width="50%"/>

Splice into these wires using the same method we used to splice the power for the interceptor (strip & weave) and then connect them to the CAN shield.

All we need to do now is find a switched 12v source for the master... I was too lazy at the time to find one and used something I already knew would work. Check the red & green wire coming out of the brown 6-pin connector (pin 4).. It should be switched... but check! Ground should be nearby too, if not, just ground it using the chassis.

Check that the unit is getting power when you turn the key and if it's all good, there is space inside the white box for it to live in.

<img src="https://github.com/ABangingDonk/OpenHaldex/blob/master/media/openhaldex_install_master_3.jpg?raw=true" width="50%"/>

Now you can close up the box, put everything back together and grab yourself a hard-earned beer.

## Sketch dependencies
### Interceptor (Due)
@collin80 [due_can](https://github.com/collin80/due_can "due_can")

@collin80 [can_common](https://github.com/collin80/can_common "can_common")

@JChristensen [Timer 2.1](https://github.com/JChristensen/Timer/tree/v2.1 "Timer")

@sebnil [DueFlashStorage](https://github.com/sebnil/DueFlashStorage "DueFlashStorage")

### Master (Uno)
@Seeed-Studio [CAN_BUS_Shield](https://github.com/Seeed-Studio/CAN_BUS_Shield "CAN_BUS_Shield")

## Issues
1. Can't seem to be able to set more than 8 points in a mode
2. App buttons can 'desync' sometimes, needing a few prods to get it to display correctly
3. Haldex behaviour doesn't track the target as closely as I'd like.. more work to be done here
4. Interceptor can miss some CAN messages sometimes and this can cause the Haldex to glitch out for a split second very occasionally
