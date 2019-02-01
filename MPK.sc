/*_____________________________________________________________
MPK.sc
MIDI control with the Akai MPK mini MKII

(C) 2019 Jonathan Reus
http://jonathanreus.com

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see http://www.gnu.org/licenses/

-------------------------------------------------------

@usage

MPK.init;
MPK.keyboardAction_({|note, vel, msg| note.postln });
MPK.knobAction_({|knob, val| knob.postln });
MPK.padTriggerAction_(\A, {|pad, vel, msg| pad.postln });
MPK.padToggleAction_(\A, {|pad, vel, msg| pad.postln });
MPK.padTriggerAction_(\B, {|pad, vel, msg| pad.postln });
MPK.padToggleAction_(\B, {|pad, vel, msg| pad.postln });
MPK.xyAction_({|xpos, ypos| [xpos, ypos].postln });


MPK Midi Codes are dependent on uploading the given configuration:

[control id, control type, midi channel, number]

[\keyboard, \keyboard, 0, -1], // keyboard responder, all keys on ch0
[\xy, \ccquad, 9, 10, 11, 12, 0], // xy is 4 cc numbers, ch0
[\k1, \cc, 0, 1],             // KNOBS are cc numbers ch0
[\k2, \cc, 0, 2],
[\k3, \cc, 0, 3],
[\k4, \cc, 0, 4],
[\k5, \cc, 0, 5],
[\k6, \cc, 0, 6],
[\k7, \cc, 0, 7],
[\k8, \cc, 0, 8],

// Pads are on ch1, note or cc in two banks

[\p1n_a, \note, 1, 44],          // Pads Bank A - Notes
[\p2n_a, \note, 1, 45],
[\p3n_a, \note, 1, 46],
[\p4n_a, \note, 1, 47],
[\p5n_a, \note, 1, 48],
[\p6n_a, \note, 1, 49],
[\p7n_a, \note, 1, 50],
[\p8n_a, \note, 1, 51],
[\p1cc_a, \cctoggle, 1, 1],      // Pads Bank A - Control Messages
[\p2cc_a, \cctoggle, 1, 2],
[\p3cc_a, \cctoggle, 1, 3],
[\p4cc_a, \cctoggle, 1, 4],
[\p5cc_a, \cctoggle, 1, 5],
[\p6cc_a, \cctoggle, 1, 6],
[\p7cc_a, \cctoggle, 1, 7],
[\p8cc_a, \cctoggle, 1, 8],
[\p1n_b, \note, 1, 32],          // Pads Bank B - Notes
[\p2n_b, \note, 1, 33],
[\p3n_b, \note, 1, 34],
[\p4n_b, \note, 1, 35],
[\p5n_b, \note, 1, 36],
[\p6n_b, \note, 1, 37],
[\p7n_b, \note, 1, 38],
[\p8n_b, \note, 1, 39],
[\p1cc_b, \cctoggle, 1, 9],      // Pads Bank B - Control Messages
[\p2cc_b, \cctoggle, 1, 10],
[\p3cc_b, \cctoggle, 1, 11],
[\p4cc_b, \cctoggle, 1, 12],
[\p5cc_b, \cctoggle, 1, 13],
[\p6cc_b, \cctoggle, 1, 14],
[\p7cc_b, \cctoggle, 1, 15],
[\p8cc_b, \cctoggle, 1, 16],
];
);

________________________________________________________________*/


MPK {
  classvar device;

  classvar <>keyboardAction, <>knobAction, <>xyAction;
  classvar padActions;

  classvar midiFuncs;
  classvar <xyVal;

  *init {
    device=nil;
    xyVal = [0,0];
    MIDIClient.init;
    MIDIClient.sources.do {|mep|
      if(mep.name == "MPK Mini Mk II") {
        device = mep;
      };
    };
    if(device.isNil) {
      "MPK Mini Device Not Found".error.throw;
    };
    MIDIIn.connect(1, device);

    midiFuncs = Dictionary.new;

    midiFuncs.put(\keyboardOn, MIDIFunc({|val,num|
      keyboardAction.(num, val, \noteOn);
    }, nil, 0, \noteOn, device.uid));

    midiFuncs.put(\keyboardOff, MIDIFunc({|val,num|
      keyboardAction.(num, val, \noteOff);
    }, nil, 0, \noteOff, device.uid));

    midiFuncs.put(\knobs, MIDIFunc({|val,num|
      knobAction.(num, val);
    }, (1..8), 0, \control, device.uid));

    midiFuncs.put(\padsOn, MIDIFunc({|val,num|
      var pad;
      if(num >= 44) { // Bank A
        pad = num-43;
        padActions.at(\An).value(pad, val, \noteOn);
      } { // Bank B
        pad = num-31;
        padActions.at(\Bn).value(pad, val, \noteOn);
      };
    }, (44..51) ++ (32..39), 1, \noteOn, device.uid));

    midiFuncs.put(\padsOff, MIDIFunc({|val,num|
      var pad;
      if(num >= 44) { // Bank A
        pad = num-43;
        padActions.at(\An).value(pad, val, \noteOff);
      } { // Bank B
        pad = num-31;
        padActions.at(\Bn).value(pad, val, \noteOff);
      };
    }, (44..51) ++ (32..39), 1, \noteOff, device.uid));

    midiFuncs.put(\padsCC, MIDIFunc({|val,num|
      var pad, toggleVal;
      if(val == 0) { toggleVal = \off } { toggleVal = \on };
      if(num <= 8) { // Bank A
        pad = num;
        padActions.at(\Acc).value(pad, val, toggleVal);
      } { // Bank B
        pad = num-8;
        padActions.at(\Bcc).value(pad, val, toggleVal);
      };
    }, (1..16), 1, \control, device.uid));

    midiFuncs.put(\xyCC, MIDIFunc({|val,num|
      switch(num,
        9, { xyVal[0] = val.linlin(0, 127, 0, -1.0) }, // left
        10, { xyVal[0] = val.linlin(0, 127, 0, 1.0) }, // right
        11, { xyVal[1] = val.linlin(0, 127, 0, 1.0) }, // up
        12, { xyVal[1] = val.linlin(0, 127, 0, -1.0) } // down
      );
      xyAction.(xyVal[0], xyVal[1]);
    }, (9..12), 0, \control, device.uid));

    // Set up default callbacks

    keyboardAction = {|key, vel, msg| "key % %: %".format(key,msg,vel).postln };
    knobAction = {|knob, val| "knob %: %".format(knob, val).postln };
    xyAction = {|xpos,ypos| "xy: % %".format(xpos,ypos).postln };

    // pad callbacks
    padActions = Dictionary.new;
    padActions.put(\An, {|pad,vel,msg| ("Pad A note"+[pad,vel,msg]).postln });
    padActions.put(\Acc, {|pad,vel,msg| ("Pad A cc"+[pad,vel,msg]).postln });
    padActions.put(\Bn, {|pad,vel,msg| ("Pad B note"+[pad,vel,msg]).postln });
    padActions.put(\Bcc, {|pad,vel,msg| ("Pad B cc"+[pad,vel,msg]).postln });
  }


  // Keyboard responder {|note,vel,msg|}
  // Knob responder {|knob, val|}
  // XY joystick responder {|xpos,ypos|}

  // Pad responders, padBank is \An or \Bn / \Acc or \Bcc
  // {|pad,vel,msg|}
  *padAction_ {|padBank, callback|
    padActions.put(padBank, callback);
  }



}
