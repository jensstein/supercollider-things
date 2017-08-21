Arpeggiator {
	// TODO: også kunne sætte arr som Pseq eller Routine
	// schedulering af ændringer -> sæt den her array af notes ved næste takts begyndelse
	// have arr til at være en liste af en custom klasse hvor man både kan sætte tonehøjde og andre værdier der skal gives videre til synthen
	var <>synth, <key, <arr, <dur = 0.3333333, dur_stream;
	var <arr_size = 3, <arr_start = 30, <arr_step = 9;
	var <>dur_ratio = 0.6, <amp = 0.2, amp_stream;
	var <>routine, <initial_routine;
	var <>array_transform_function;
	var <>note_correct = nil;
	var <>synth_args;
	var <>break_loop = false;
	var <>scheduler;
	*new {arg synth_name, key;
		^super.new.init(synth_name, key);
	}
	init {arg synth_name, key_name;
		this.set_note_correct;
		scheduler = TempoClock.default;
		amp_stream = amp;
		dur_stream = dur;
		synth = synth_name;
		synth_args = Dictionary.new;
		if(key_name.isNil, {
			key = (synth_name ++ Date.localtime).asSymbol;
		}, {
			key = key_name.asSymbol;
		});
		array_transform_function = {arg arr; arr};
		arr = Array.series(arr_size, arr_start, arr_step);
		routine = Tdef((key + "_arp").asSymbol, {
			loop{
				// se under Function for helpfile til block
				block{arg break;
					var transformed_arr = array_transform_function.value(arr);
					transformed_arr.do({arg item;
						var snth;
						var dur_cur = dur_stream.next;
						if(item.class != Symbol, {
							note_correct !? item = note_correct.value(item) ?? item = item;
							snth = Synth(synth, [\freq, item.midicps, \dur, dur_cur * dur_ratio, \amp, amp_stream.next] ++ synth_args.asKeyValuePairs);
							// NodeWatcher gør at man kan bruge Node.isPlaying og Node.isRunning
							NodeWatcher.register(snth, true);
						});
						dur_cur.wait;
						if(snth.isNil.not.and(snth.isPlaying), {
							snth.release;
						});
						if(break_loop == true, {
							break_loop = false;
							break.value;
						});
					});
					//0.02.wait; // en kort wait sørger for at routinen ikke bliver overbelastet så den ikke svarer
				}
			}
		});
		initial_routine = routine;
	}
	arr_{arg new_arr;
		arr = new_arr;
		// nødvendigt for at kunne genstarte do-loops
		// der fx kører på en uendelig Pseq
		break_loop = true;
	}
	arr_size_{arg size;
		arr = Array.series(size, arr_start, arr_step);
		arr_size = size;
	}
	arr_start_{arg start;
		arr = Array.series(arr_size, start, arr_step);
		arr_start = start;
	}
	arr_step_{arg step;
		arr = Array.series(arr_size, arr_start, step);
		arr_step = step;
	}
	// convert to stream to allow calling next from playing routine
	stream_factory {arg value;
		if(value.isKindOf(Array),
			{^Pseq(value, inf).asStream},
			{^value.asStream}
		);
	}
	amp_{arg new_amp;
		amp_stream = this.stream_factory(new_amp);
		amp = new_amp;
	}
	dur_{arg new_dur;
		scheduler.play({
			// hvorfor to forskellige variable her?
			dur_stream = this.stream_factory(new_dur);
			dur = new_dur;
			routine.reset
		}, scheduler.timeToNextBeat);
	}
	play {
		routine.play;
	}
	stop {
		routine.stop;
		routine.reset;
	}
	pyramid {
		array_transform_function = {arg arr; arr.pyramid};
	}
	mirror {
		array_transform_function = {arg arr; arr.mirror};
	}
	set_note_correct {arg scale = #[0, 2, 4, 5, 7, 9, 11];
		// finder den tone i skalaen, som er tættest på den givne tone
		note_correct = {arg item;
			if(item.class == Symbol, {item}, {
				if(item.class == Array, {
					item.collect({arg arr_item;
						this.findMatchByScale(scale, arr_item);
					});
				}, {
					this.findMatchByScale(scale, item)
				});
			});
		}
	}
	findMatchByScale {arg scale, item;
		var base = (item / 10).floor * 10;
		var match = (item % (base + scale));
		var close = scale[match.find((match.copy.sort)[0].asArray)];
		^(base + close);
	}
	set_notes {arg notes;
		// this ser ud til at være nødvendig her for at få ændringer til at slå igennem
		this.arr = notes.collect({arg note;
			note.tone;
		});
		this.dur = notes.collect({arg note;
			note.dur;
		});
		this.amp = notes.collect({arg note;
			if(note.amp.isNil.not, {
				note.amp;
			}, {amp});
		});
	}
	clear_note_correct {
		note_correct = nil;
	}
}

ArpNote {
	var <>tone, <>dur, <>amp;
	*new{arg tone, dur, amp;
		^super.new.init(tone, dur, amp);
	}
	init{arg toneArg, durArg, ampArg;
		tone = toneArg;
		dur = durArg;
		amp = ampArg;
	}
}

// alias for ArpNote
AN : ArpNote{}
