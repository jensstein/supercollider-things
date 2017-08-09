/* 
Inspired by the NanoKontrol.sc of Jesús Gollonet:
https://github.com/jesusgollonet/NanoKontrol.sc

TODO:
	free MIDIdefs
	Controller and Pad as private classes or rename them

	touch, polytouch
	sætte tempo fra applikation, midi clock
*/

MPD26 {
	// access controls directly through dictionaries
	// instead of via doesNotUnderstand as in NanoKontrol.sc
	var <ctls, <faders, <knobs, <pads;
	*new {
		^super.new.init_MPD26;
	}
	init_MPD26 {
		var endpoint;
		MIDIClient.init;
		endpoint = MIDIIn.findPort(
			"Akai MPD26-Akai MPD26 MIDI 1",
			"Akai MPD26-Akai MPD26 MIDI 1"
		);
		if(endpoint !== nil, {
			MIDIIn.connect(0, endpoint);
		}, {
			MIDIIn.connectAll;
			"Couldn't find MPD26 in midi sources, connecting all".warn;
		});
		pads = IdentityDictionary(know: true);
		pads.put(\bank_a, IdentityDictionary(know: true));
		pads.put(\bank_b, IdentityDictionary(know: true));
		pads.put(\bank_c, IdentityDictionary(know: true));
		pads.put(\bank_d, IdentityDictionary(know: true));
		(36..51).do({arg val, index;
			pads.bank_a.put((\pad ++ (index + 1)).asSymbol, Pad((\bank_a ++ \pad ++ (index + 1)).asSymbol, val, index + 1));
		});
		(52..67).do({arg val, index;
			pads.bank_b.put((\pad ++ (index + 1)).asSymbol, Pad((\bank_b ++ \pad ++ (index + 1)).asSymbol, val, index + 1));
		});
		(68..83).do({arg val, index;
			pads.bank_c.put((\pad ++ (index + 1)).asSymbol, Pad((\bank_c ++ \pad ++ (index + 1)).asSymbol, val, index + 1));
		});
		(84..99).do({arg val, index;
			pads.bank_d.put((\pad ++ (index + 1)).asSymbol, Pad((\bank_d ++ \pad ++ (index + 1)).asSymbol, val, index + 1));
		});
		knobs = IdentityDictionary[
			\k1 -> Controller(\k1, 3, 1),
			\k2 -> Controller(\k2, 9, 2),
			\k3 -> Controller(\k3, 14, 3),
			\k4 -> Controller(\k4, 15, 4),
			\k5 -> Controller(\k5, 16, 5),
			\k6 -> Controller(\k6, 17, 6)
		];
		faders = IdentityDictionary[
			\f1 -> Controller(\f1, 20, 1),
			\f2 -> Controller(\f2, 21, 2),
			\f3 -> Controller(\f3, 22, 3),
			\f4 -> Controller(\f4, 23, 4),
			\f5 -> Controller(\f5, 24, 5),
			\f6 -> Controller(\f6, 25, 6)
		];
		ctls = IdentityDictionary.new;
		ctls.putAll(pads, knobs, faders);
	}
	on_touch_{arg action;
		MIDIdef.touch(\touch, {arg val;
			action.value(val);
		});
	}
}

Controller {
	var key, <num, <index, <value;
	*new {arg key, num, index;
		^super.newCopyArgs(key, num, index).init_controller;
	}
	init_controller {
		// set here to activate the default action of
		// saving the value of the controller
		this.on_changed = {};
		this.note_on = {};
		this.note_off = {};
	}
	on_changed_{arg action;
		MIDIdef.cc((key ++ "control").asSymbol, {arg val;
			value = val;
			action.value(val)
		},
		num);
	}
	// stubs
	note_on_{arg action;}
	note_off_{arg action;}
}

Pad : Controller {
	*new {arg key, num, index;
		^super.newCopyArgs(key, num, index).init_controller;
	}
	note_on_{arg action;
		MIDIdef.noteOn((key ++ "on").asSymbol,
		{arg velocity;
			value = 1;
			action.value(velocity);
		},
		num);
	}
	note_off_{arg action;
		MIDIdef.noteOff((key ++ "off").asSymbol,
		{arg velocity;
			value = 0;
			action.value(velocity);
		},
		num)
	}
}
