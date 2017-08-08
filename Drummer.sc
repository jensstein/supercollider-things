Drummer {
	// TODO:
	// amp_array skal være et dictionary. array-indeksering virker ikke når x-værdien kan være et decimaltal
	// rutinen starter forfra hver gang den stopped med mellemrum
	// mute-checkbox ved hver sample
	// højreklik -> fyld hver 3./8./16./24./etc

	// skal have en <>scheduler ligesom Arpeggiator og et værn mod for høje tempi

	var <paths;
	var window;
	var samples_view_width = 100, sliders_view_height = 100;
	var btn_width = 30, btn_height = 30;
	var padding = 30;
	var samples_view, patterns_view, sliders_view, tempo_text, tempo_numberbox, quant_numberbox, pattern_number_text, pattern_numberbox;
	var btns_values;
	var max_amp = 1;
	var amp_array;
	var selected_sample = 0;
	var <>beats = 4;
	var cols = 16;
	var rows;
	var <>synth_args;
	var <busses;
	var <group;
	var routine;
	var sample_texts, slider_array;

	// kan ikke klare 0.0001
	//var precision = 0.001;
	var precision = 0.0625; // 8 / 128
	var <>dots;
	var <dots_index = 0;
	var dot_size = 10;
	var snap_to_xgrid = true;
	var patterns_offset;

	var pattern_change_btn, pattern_new_btn;
	var patterns_height, patterns_width;

	var buffers;

	var synth, key;

	*new {arg samples_path, synth_arg, key_arg;
		^super.new.init(samples_path, synth_arg, key_arg);
	}
	init {arg samples_path, synth_arg, key_arg;
		var paths = PathName(samples_path).files;
		if(synth_arg.isNil, {
			synth = "sample_player";
		}, {
			synth = synth_arg;
		});
		if(key_arg.isNil, {
			key = (synth ++ Date.localtime).asSymbol;
		}, {
			key = key_arg.asSymbol;
		});

		patterns_offset = btn_height / 2;
		rows = paths.size;
		sample_texts = Array.new(rows);
		slider_array = Array.new(rows);
		synth_args = Array.new(rows);
		rows.do({arg item;
			synth_args = synth_args.add(Dictionary.new);
		});
		// dictionary for named busses
		busses = Dictionary.new;
		group = this.register_group;
		dots = Array.new;
		// an array inside another array cannot expand the same way as the outer array
		dots = dots.add(Array.new(rows * cols));
		amp_array = Array.fill2D(rows, cols, {max_amp});
		buffers.do({arg buf;
			buf.free;
		});
		buffers = Array.newClear(rows);
		paths.do({arg item, index;
			buffers[index] = Buffer.read(Server.default, item.fullPath);
		});
		window = Window("drummer", Rect(0, 0, 800, 600), scroll:true);
		// fromHexString can only set rgb, not alpha
		window.view.background = Color.fromHexString("#eeeeee");
		// XXX: kun qt-gui
		// fjern udkommentering når koden er klar
		//window.view.deleteOnClose = false;
		window.front;

		tempo_text = StaticText(window, Rect(window.view.bounds.left + padding, window.view.bounds.top + padding, 60, 40));
		tempo_text.string = "tempo: ";
		tempo_numberbox = NumberBox(window, Rect(tempo_text.bounds.left + tempo_text.bounds.width, window.view.bounds.top + padding, 40, 40));
		tempo_numberbox.value = TempoClock.tempo * 60;
		quant_numberbox = NumberBox(window, Rect(tempo_numberbox.bounds.right + padding, window.view.bounds.top + padding, 40, 40));
		quant_numberbox.value = cols;
		pattern_number_text = StaticText(window, Rect(quant_numberbox.bounds.right + padding, window.view.bounds.top + padding, 50, 40));
		pattern_number_text.string = "pattern: ";
		pattern_numberbox = NumberBox(window, Rect(pattern_number_text.bounds.right + padding, window.view.bounds.top + padding, 40, 40));
		pattern_change_btn = Button(window, Rect(pattern_numberbox.bounds.right + padding, window.view.bounds.top + padding, 40, 40));
		pattern_change_btn.mouseDownAction = {
			this.change_pattern;
		};
		pattern_new_btn = Button(window, Rect(pattern_change_btn.bounds.right + padding, window.view.bounds.top + padding, 40, 40));
		pattern_new_btn.mouseDownAction = {
			this.new_pattern;
		};

		// userviewets højde er skyld i at drawFunc blokerer hvis der er mange samples
		samples_view = View(window, Rect(window.view.bounds.left + padding, tempo_numberbox.bounds.bottom, samples_view_width, rows * btn_height));
		// TODO: hvorfor virker resize ikke
		patterns_view = UserView(window, Rect(samples_view.bounds.right, samples_view.bounds.top, window.bounds.width - samples_view.bounds.right, (rows * btn_height) + patterns_offset));
		sliders_view = View(window, Rect(patterns_view.bounds.left + padding, patterns_view.bounds.bottom, patterns_view.bounds.width, sliders_view_height));

		// TODO: skal være get-metoder så der kan blive taget højde for resizing
		patterns_width = patterns_view.bounds.width - padding;
		patterns_height = patterns_view.bounds.height - patterns_offset;

		rows.do({arg i;
			var text;
			text = StaticText(samples_view, Rect(0, i * btn_height, 100, 30));
			text.string = paths[i].fileNameWithoutExtension;
			if(i == selected_sample, {
				// StaticText.background kan ikke sættes direkte som hex - skal gennem Color
				text.background = Color.fromHexString((window.view.background.toLong - 0x22222200).asHexString);
			}, {
				text.background = window.view.background;
			});
			text.mouseDownAction = {arg view, x, y, modifiers, buttonNumber, clickCount;
				switch(buttonNumber,
				// left click
				0, {
					var new_background;
					this.play_synth(i);
					selected_sample = i;
					sample_texts.do({arg item;
						item.background = window.view.background;
					});
					// toLong: custom addition to Color to convert from rgba to long
					new_background = window.view.background.toLong - 0x22222200;
					text.background = Color.fromHexString(new_background.asHexString);
					slider_array.do({arg slider, slider_index;
						slider.value = amp_array[selected_sample][slider_index];
					});
				},
				// right click
				1, {
					var width = patterns_width / (cols / 2);
					// - width for ikke at have et slag for meget til sidst
					(0,width..(patterns_width - width)).do({arg item;
						// TODO: padding og patterns_offset skal ikke medregnes her - måske have separat metode til at håndtere relative x,y-værdier
						this.toggle_dot(item + padding, i * (patterns_height / rows) + patterns_offset);
					});
					this.update_ui;
				});
			};
			sample_texts = sample_texts.add(text);
		});
		window.view.keyDownAction = {
			arg view, char, modifiers, unicode, keycode;
			if(unicode == 32, {
				if(routine.isPlaying, {
					routine.pause;
				}, {
					routine.play;
				});
			});
		};
		tempo_numberbox.action = {arg view;
			TempoClock.tempo = (view.value / 60);
		};
		quant_numberbox.action = {arg view;
			cols = view.value;
			this.update_ui;
		};
		pattern_numberbox.action = {arg view;
			this.change_pattern(view.value.asInteger);
		};
		patterns_view.drawFunc = {
			var x = patterns_width / cols;
			var y = patterns_height / rows;
			rows.do({arg i;
				// TODO: synkronisere mellem padding og offset her og beregning af x og y i mouseDownAction
				Pen.moveTo(Point(padding, (i * y) + patterns_offset));
				Pen.lineTo(Point(patterns_view.bounds.width - x, (i * y) + patterns_offset));

				Pen.stroke;
				cols.do({arg j;
					if((j % beats) == 0, {
						Pen.width = 4;
					}, {
						Pen.width = 1;
					});
					Pen.moveTo(Point((j * x) + padding, patterns_offset));
					Pen.lineTo(Point((j * x) + padding, patterns_view.bounds.height - y));

					Pen.stroke;
				});
			});
			dots[dots_index].do({arg dot;
				var dot_x, dot_y;
				var half_size = dot_size / 2;
				// tilføj padding og offset her så dot-objekterne kan have de rigtige indeks-værdier uden offset
				dot_x = (dot.x * patterns_width / beats) + padding;
				dot_y = (dot.sample * patterns_height / rows) + patterns_offset;
				Pen.addOval(Rect(dot_x - half_size, dot_y - half_size, dot_size, dot_size));
			});
			Pen.fill;
		};
		patterns_view.resize = 2;
		patterns_view.mouseDownAction = {arg view, x, y, modifiers, buttonNumber, clickCount;
			this.toggle_dot(x, y);
			this.update_ui;
		};
		routine = Tdef(key, {
			var tick = 0;
			//TempoClock.beatsPerBar = beats;
			loop{
				// floats kan ikke sammenlignes med == på grund af usikkerhed
				// http://new-supercollider-mailing-lists-forums-use-these.2681727.n2.nabble.com/float-summing-rounding-weirdness-td7619297.html
				// brug equalWithPrecision og round(precision)
				dots[dots_index].do({arg item;
					var diff = item.x - tick;
					if((diff >= 0).and(diff < precision), {
						//("tick: " + tick + " x: " + item.x).postln;
						this.play_synth(item.sample);
					});
				});
				// måske prøve at rette til hvis tempoet er skredet
				// potentiel bug hvis beat ikke er 4
				tick = tick + precision;
				// hvis tick er over beats er tempoet skredet en lille smule
				// det sker hvis man ændrer tempoet undervejs
				// eller kan det være hvis de andre rutiner venter for længe fordi de har højere wait-værdier så der går længere inden de opfanger temposkiftet
				if((tick % beats == 0).or(tick > beats), {tick = 0});
				precision.wait;
			}
		}).play;
	}
	// TODO: fjerne slider igen, hvis prikken fjernes
	// kun vise de relevante slider, når den valgte sample ændres + huske værdier
	set_sliders {
		dots[dots_index].do({arg i;
			var slider;
			//slider = Slider(sliders_view, Rect(i * (w_width - 50 / cols), 0, 20, 50));
			slider = Slider(sliders_view, Rect((i.x / beats) * (sliders_view.bounds.width - padding), 0, 20, 50));
			slider.value = 1;
			slider.action = {
				amp_array[selected_sample][i] = slider.value;
			};
			slider_array = slider_array.add(slider);
		});
	}
	list_min {arg list;
		// returns index of min in a list
		var cur_min = [list[0], 0];
		list[1..].do({arg item, index;
			if(item < cur_min[0], {
				// offset by one so index will be correct when returning
				cur_min = [item, index + 1];
			});
		});
		^cur_min[1];
	}
	toggle_dot {arg x, y;
		var removed = false;
		var sample_index, xi, xs, ys, new_x, new_y;
		// possible values for y
		ys = Array.fill(rows, {arg index;
			index * (patterns_height / rows);
		});
		// pick nearest value from possible ys
		sample_index = this.list_min(abs(ys - (y - patterns_offset)));
		new_y = ys[sample_index];
		new_y = new_y + patterns_offset;
		if(snap_to_xgrid, {
			xs = Array.fill(cols, {arg index;
				index * (patterns_width / cols);
			});
			new_x = xs[this.list_min(abs(xs - (x - padding)))];
			xi = (new_x / patterns_width).round(0.001) * beats;
		}, {
			xi = (x / patterns_width) * beats;
		});
		dots[dots_index].do({arg item;
			block{arg break;
				var diff = item.x - xi;
				if(((diff >= 0).and(diff < (TempoClock.beatDur * precision)).and(sample_index == item.sample)), {
					// select giver en ny collection tilbage, så hvis man bruger den på en variabel der peger på en underarray bliver ens reference tabt
					dots[dots_index] = dots[dots_index].select({arg i;
						// retain items where i and xi does not match
						((i.sample == item.sample).and(i.x == item.x)).not;
					});
					removed = true;
					break.value;
				});
			}
		});
		if(removed.not, {
			dots[dots_index] = dots[dots_index].add(DrumPoint(xi, sample_index));
		});
	}
	update_ui {
		AppClock.play({
			this.set_sliders;
			sliders_view.refresh;
			patterns_view.refresh;
			pattern_numberbox.value = dots_index;
			tempo_numberbox.value = TempoClock.tempo * 60;
		});
	}
	change_pattern {arg index;
		if(index.isNil.not, {
			if(index.class != Integer, {
				"index must be integer".error;
				^nil;
			});
			if((index >= dots.size).or(index < 0), {
				("index outside range (max " + (dots.size - 1).asString ++ "): " + index).error;
				^nil;
			});
			dots_index = index;
		}, {
			dots_index = dots_index + 1;
			if(dots_index >= dots.size, {
				dots_index = 0;
			});
		});
		this.update_ui;
	}
	new_pattern {arg empty = false;
		var current_dots;
		if(empty, {
			current_dots = Array.new(rows * cols);
		}, {
			current_dots = dots[dots_index].copy;
		});
		dots_index = dots_index + 1;
		dots = dots.add(current_dots);
		this.update_ui;
	}
	play_synth {arg index = 0;
		// ensures that the synths play after thisProcess.stop
		// måske en bug her hvor antallet af grupper bliver ved med at vokse
		if(group.isPlaying.not, {
			group = this.register_group;
		});
		Synth.head(group, "sample_player", [\bufnum, buffers[index]] ++ synth_args[index].asKeyValuePairs);
	}
	register_group {
		var grp = Group.new(Server.default);
		// it seems like registration with NodeWatcher must happen immediately after creating the group
		NodeWatcher.register(grp);
		^grp;
	}
	read_file {arg file_path;
		var file, lines, array;
		file = File(file_path, "r");
		array = Array.new;
		// $ to get a Char
		lines = file.readAllString.split($\n);
		lines.do({arg l;
			var parts;
			// format is
			// pattern_index:
			// sample,x
			if("^[0-9]+:".matchRegexp(l), {
				array = array.add(Array.new);
			});
			parts = l.split($,);
			if(parts.size == 2, {
				array[array.size - 1] = array[array.size - 1].add(DrumPoint(parts[1].asFloat, parts[0].asFloat));
			});
		});
		file.close;
		^array;
	}
}

DrumPoint {
	var <x, <sample;
	*new {arg x, sample;
		^super.new.init(x, sample);
	}
	init {arg x_arg, sample_arg;
		x = x_arg;
		sample = sample_arg;
	}
}
