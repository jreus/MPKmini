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
  classvar <device;
  classvar <initialized = false;

  classvar <keyboardActions, <knobActions, <xyActions;
  classvar <padActions;

  classvar <midiFuncs;
  classvar <xyVal;

  classvar <knobValues;

  classvar <program=0;
  classvar <programNames, <programColors;

  classvar <win, <progText;

  *init {
    if(initialized.not) {
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

      programNames = 8.collect(_.asString);
      programColors = [
        Color.black,
        Color.blue,
        Color.new(0.2, 0.7, 1),
        Color.red,
        Color.new(1, 0.7, 0.5),
        Color.grey,
        Color.new(0.5, 0.5, 1),
        Color.magenta
      ];
      knobValues = 8.collect {|it|
        Array.fill(8, {0});
      };

      // Set up default callbacks
      keyboardActions = 8.collect({|it|
        {|key, vel, msg| "p%: key % %: %".format(it, key,msg,vel).postln }
      });
      knobActions = 8.collect({|it|
        {|knob, val|
          "p%: knob %: %".format(it, knob, val).postln;
        }
      });
      xyActions = 8.collect({|it|
        {|xpos, ypos| "p%: xy: % %".format(it, xpos, ypos).postln }
      });

      // pad callbacks
      padActions = 8.collect({|it|
        var padsets = Dictionary.new;
        padsets.put(\An, {|pad,vel,msg| "p%: Pad A note %, %, %".format(it, pad, vel, msg).postln });
        padsets.put(\Acc, {|pad,vel,msg| "p%: Pad A cc %, %, %".format(it, pad, vel, msg).postln });
        padsets.put(\Bn, {|pad,vel,msg| "p%: Pad B note %, %, %".format(it, pad, vel, msg).postln });
        padsets.put(\Bcc, {|pad,vel,msg| "p%: Pad B cc %, %, %".format(it, pad, vel, msg).postln });
        padsets;
      });


      // Set up MIDI responder functions
      midiFuncs = Dictionary.new;

      midiFuncs.put(\progChange, MIDIFunc({|val,num|
        this.program_(val);
      }, nil, 1, \program, device.uid));

      midiFuncs.put(\keyboardOn, MIDIFunc({|val,num|
        keyboardActions[program].value(num, val, \noteOn);
      }, nil, 0, \noteOn, device.uid));

      midiFuncs.put(\keyboardOff, MIDIFunc({|val,num|
        keyboardActions[program].value(num, val, \noteOff);
      }, nil, 0, \noteOff, device.uid));

      midiFuncs.put(\knobs, MIDIFunc({|val,num|
        // Update Knob value
        knobValues[program][num] = val;
        knobActions[program].value(num, val);

      }, (1..8), 0, \control, device.uid));

      midiFuncs.put(\padsOn, MIDIFunc({|val,num|
        var pad;
        if(num >= 44) { // Bank A
          pad = num-43;
          padActions[program].at(\An).value(pad, val, \noteOn);
        } { // Bank B
          pad = num-31;
          padActions[program].at(\Bn).value(pad, val, \noteOn);
        };
      }, (44..51) ++ (32..39), 1, \noteOn, device.uid));

      midiFuncs.put(\padsOff, MIDIFunc({|val,num|
        var pad;
        if(num >= 44) { // Bank A
          pad = num-43;
          padActions[program].at(\An).value(pad, val, \noteOff);
        } { // Bank B
          pad = num-31;
          padActions[program].at(\Bn).value(pad, val, \noteOff);
        };
      }, (44..51) ++ (32..39), 1, \noteOff, device.uid));

      midiFuncs.put(\padsCC, MIDIFunc({|val,num|
        var pad, toggleVal;
        if(val == 0) { toggleVal = \off } { toggleVal = \on };
        if(num <= 8) { // Bank A
          pad = num;
          padActions[program].at(\Acc).value(pad, val, toggleVal);
        } { // Bank B
          pad = num-8;
          padActions[program].at(\Bcc).value(pad, val, toggleVal);
        };
      }, (1..16), 1, \control, device.uid));

      midiFuncs.put(\xyCC, MIDIFunc({|val,num|
        switch(num,
          9, { xyVal[0] = val.linlin(0, 127, 0, -1.0) }, // left
          10, { xyVal[0] = val.linlin(0, 127, 0, 1.0) }, // right
          11, { xyVal[1] = val.linlin(0, 127, 0, 1.0) }, // up
          12, { xyVal[1] = val.linlin(0, 127, 0, -1.0) } // down
        );
        xyActions[program].value(xyVal[0], xyVal[1]);
      }, (9..12), 0, \control, device.uid));

    } {
      "MPK already initialized".error;
    };

  }

  *program_ {|prog|
    program = prog;
    "Program Change to %".format(programNames[prog]).warn;
    if(progText.notNil) {

      {
        progText.string_((program+1).asString);
        4.do {
          progText.background_(Color.rand);
          0.05.wait;
        };
        progText.background_(programColors[prog]);
      }.fork(AppClock);

    };
  }


  // Keyboard responder {|note,vel,msg|}
  *keyAction_ {|callback, prog=0|
    keyboardActions[prog] = callback;
  }

  // XY joystick responder {|xpos,ypos|}
  *xyAction_ {|callback, prog=0|
    xyActions[prog] = callback;
  }

  // Knob responder {|knob, val|}
  *knobAction_ {|callback, prog=0|
    knobActions[prog] = callback;
  }

  // Pad responders, padBank is \An or \Bn / \Acc or \Bcc
  // {|pad,vel,msg|}
  *padAction_ {|padBank, callback, prog=0|
    padActions[prog].put(padBank, callback);
  }

  *gui {|position|
    var win, top=0, left=0, width=100, height=100;
    var styler, childView;
    if(win.notNil) {
      if(win.isClosed.not) {
        win.front;
        ^win;
      }
    };

    if(position.notNil) {
      top=position.y; left=position.x;
    };

    win = Window("MPKmini", Rect(left,top,width,height));
    styler = GUIStyler(win);

    childView = styler.getView("MPK", win.view.bounds, gap: 10@10);

    styler.getSizableText(childView, "program", 100);
    progText = styler.getSizableText(childView, (program+1), 100, fontSize: 64, bold: true);

    ^win.alwaysOnTop_(true).front;

  }

}
