/*
Classes to send and receive sysex messages to and from the Korg nanoKONTROL (1)
Scheme of MIDI implementation can be found in nanoKONTROL_MIDIimp.txt (http://www.korg.com/us/support/download/product/1/252/)
*/

NKSysex {
	var <>midi_out, <>scene_name, <current_scene, <midi_channel, <device_id, <blocks, <transport_switches, <transport_sw_midi_ch, <>verbose = false;
	*new {
		^super.new.init_nk();
	}
	init_nk {arg midi_out_arg;
		var request_id = 0x0f;
		var endpoint;
		current_scene = 0;
		// array of NKBlock objects
		blocks = Array(9);
		// array of NKTransportSwitch objects
		transport_switches = Array(6);

		MIDIClient.init;
		endpoint = MIDIIn.findPort(
			"nanoKONTROL-nanoKONTROL MIDI 1",
			"nanoKONTROL-nanoKONTROL MIDI 1"
		);
		if(endpoint !== nil, {
			MIDIIn.connect(0, endpoint);
		}, {
			MIDIIn.connectAll;
			"Couldn't find nanoKONTROL in midi sources, connecting all".warn;
		});
		if(midi_out_arg != nil, {
			midi_out = midi_out_arg;
		}, {
			// it seems that you cannot connect to the correct port with MIDIOut.new on linux
			// you have to connect to the global port (0) and then use MIDIOut.connect with the correct port explicitly
			midi_out = MIDIOut(0);
			midi_out.connect(1);
		});

		device_id = this.get_id(request_id);
		// place in MIDIdef so dump is first made when device_id has been found
		MIDIdef.sysex(\get_id_and_dump, {arg data, uid;
			if((Int8Array.newFrom(data.slice((0..3))) == Int8Array[0xf0, 0x42, 0x50, 0x01]).and(data[5] == request_id), {
				this.dump_data;
			});
			MIDIdef(\get_id_and_dump).free;
		});

		// maybe not safe to define here when refering to device_id
		MIDIdef.sysex(\check_data_dump, {arg data, uid;
			if(data.size >= 9, {
				if(Int8Array.newFrom(data.slice((0..7))) == Int8Array[0xf0, 0x42, 0x40 + device_id, 0x00, 0x01, 0x04, 0x00, 0x5f], {
					switch(data[8],
					0x23, {"Data load completed".postln},
					0x24, {"Data load error".warn},
					0x21, {"Write completed".postln},
					0x22, {"Write error".warn},
					0x4f, {
						"scene changed to: %\n".postf(data[9]);
						current_scene = data[9];
					},
					{"default".postln}
					);
				});
			});
		});

		MIDIdef.sysex(\post_sysex, {arg data, uid;
			if(verbose != false, {
				(uid + ": Int8Array[").post;
				data.do({arg item, index; 
					var hex = item.asHexString;
					var end_char = ", ";
					if(index == (data.size - 1), {end_char = ""});
					(hex[hex.size - 2] ++ hex[hex.size - 1] ++ end_char).post});
				"]".postln;
			});
		});
	}
	get_id {arg request_id = 0x01;
		var search_device = Int8Array[0xf0, 0x42, 0x50, 0x00, request_id, 0xf7];
		// request fails if echo-back id is over 0x0f (not documented)
		request_id = request_id.wrap(0x00, 0x0f);
		midi_out.sysex(search_device);
		MIDIdef.sysex(\search_device, {arg data, uid; 
			if(Int8Array.newFrom(data.slice((0..3))) == Int8Array[0xf0, 0x42, 0x50, 0x01], {
				// search device reply received
				if(data[5] == request_id, 
					{
						"found device id: %\n".postf(data[4].asHexString);
						// cannot use a return here since the MIDIdef will call back asynchronously
						device_id = data[4];
					}, 
					{("request id mismatch: " + request_id + " : " + data[5]).warn}
				);
			});
		});
	}
	dump_data {
		midi_out.sysex(Int8Array[0xf0, 0x42, 0x40 + device_id, 0x00, 0x01, 0x04, 0x00, 0x1f, 0x10, 0x00, 0xf7]);
		MIDIdef.sysex(\data_dump, {arg data, uid;
			if(data.size > 11, {
				if(Int8Array.newFrom(data.slice((0..12))) == Int8Array[0xf0, 0x42, 0x40 + device_id, 0x00, 0x01, 0x04, 0x00, 0x7f, 0x7f, 0x02, 0x02, 0x26, 0x40], {
					var scene_data = data.slice((13..(data.size - 2)));
					// remove every 8th byte -which is always 0
					// must be put back when data is to be written to the device
					scene_name = "";
					blocks = Array(9);
					transport_switches = Array(6);

					scene_data = scene_data.select({arg item, index; index % 8 != 0});
					scene_data.slice((0..11)).do({arg item; scene_name = scene_name ++ item.asAscii});
					midi_channel = scene_data[12];
					// blocks
					(0..8).do({arg item;
						var offset = item * 23;
						var block_midi_ch = scene_data[16 + offset];
						var slider = NKSliderKnob(scene_data[17 + offset], scene_data[18 + offset], scene_data[19 + offset], scene_data[20 + offset]);
						var knob = NKSliderKnob(scene_data[21 + offset], scene_data[22 + offset], scene_data[23 + offset], scene_data[24 + offset]);
						var sw_a = NKSwitch(scene_data[25 + offset], scene_data[26 + offset], scene_data[27 + offset], scene_data[28 + offset], scene_data[29 + offset], scene_data[30 + offset], scene_data[31 + offset]);
						var sw_b = NKSwitch(scene_data[32 + offset], scene_data[33 + offset], scene_data[34 + offset], scene_data[35 + offset], scene_data[36 + offset], scene_data[37 + offset], scene_data[38 + offset]);
						var block = NKBlock(block_midi_ch, slider, knob, sw_a, sw_b);
						blocks.insert(item, block);
					});
					transport_sw_midi_ch = scene_data[224];
					// transport switches
					(0..5).do({arg item;
						var offset = item * 5;
						var tr_switch = NKTransportSwitch(scene_data[225 + offset], scene_data[226 + offset], scene_data[227 + offset], scene_data[228 + offset], scene_data[229 + offset]);
						transport_switches.add(tr_switch);
					});
				});
			});
		});
	}
	change_scene {arg scene = 0;
		midi_out.sysex(Int8Array[0xf0, 0x42, 0x40 + device_id, 0x00, 0x01, 0x04, 0x00, 0x1f, 0x14, 0x00 + scene.wrap(0, 3), 0xf7]);
	}
	send_inquiry_message {
		midi_out.sysex(Int8Array[0xf0, 0x7e, device_id, 0x06, 0x01, 0xf7]);
	}
	dump_scene_data {
		var dump_request = Int8Array[0xf0, 0x42, 0x40 + device_id, 0x00, 0x01, 0x04, 0x00, 0x7f, 0x7f, 0x02, 0x02, 0x26, 0x40];
		var data = Int8Array[];
		// scene name must be 12 bytes
		scene_name = NKSysex.pad_string(scene_name, " ", 12);
		scene_name.do({arg item;
			data = data.add(item.ascii);
		});
		data = data.add(midi_channel);
		// 13~15: dummy bytes
		data = data ++ Int8Array[0, 0, 0];
		blocks.do({arg block;
			data = data.add(block.midi_channel);
			data = data ++ block.slider.asInt8Array;
			data = data ++ block.knob.asInt8Array;
			data = data ++ block.sw_a.asInt8Array;
			data = data ++ block.sw_b.asInt8Array;
		});
		// 223: dummy byte
		data = data.add(0);
		data = data.add(transport_sw_midi_ch);
		transport_switches.do({arg switch;
			data = data ++ switch.asInt8Array;
		});
		// 255: dummy byte
		data = data.add(0);
		// insert 0 every 8th byte
		(0..36).do({arg item;
			data = data.insert(item * 8, 0);
		});
		if(data.size != 293, {
			("data has incorrect size: " + data.size).warn;
		}, {
			dump_request = dump_request ++ data;
			dump_request = dump_request.add(0xf7);
			midi_out.sysex(dump_request);
		});
	}
	write_request {arg dest_scene = nil;
		if(dest_scene == nil, {dest_scene = current_scene});
		midi_out.sysex(Int8Array[0xf0, 0x42, 0x40 + device_id, 0x00, 0x01, 0x04, 0x00, 0x1f, 0x11, 0x00 + dest_scene.wrap(0, 3), 0xf7]);
	}
	*pad_string {arg string, pad_char = " ", length = 12;
		var return_string = "";
		if(string.size >= length, {
			string.slice((0..(length - 1))).do({arg item;
				return_string = return_string ++ item;
			});
		}, {
			var missing = length - string.size;
			return_string = string;
			(0..missing).do({
				return_string = return_string ++ pad_char;
			});
		});
		^return_string;
	}
}

NKBlock {
	var <midi_channel, <slider, <knob, <sw_a, <sw_b;
	*new {arg midi_channel, slider, knob, sw_a, sw_b;
		^super.newCopyArgs(midi_channel, slider, knob, sw_a, sw_b);
	}
}

NKSliderKnob {
	var <>assign_type, <>cc, <>min_val, <>max_val;
	*new {arg assign_type, cc, min_val, max_val;
		^super.newCopyArgs(assign_type, cc, min_val, max_val);
	}
	asInt8Array {
		^Int8Array[assign_type, cc, min_val, max_val];
	}
}

NKSwitch {
	var <>assign_type, <>cc, <>off_val, <>on_val, <>attack_val, <>release_time, <>switch_type;
	*new {arg assign_type, cc, off_val, on_val, attack_val, release_time, switch_type;
		^super.newCopyArgs(assign_type, cc, off_val, on_val, attack_val, release_time, switch_type);
	}
	asInt8Array {
		^Int8Array[assign_type, cc, off_val, on_val, attack_val, release_time, switch_type];
	}
}

NKTransportSwitch {
	var <>assign_type, <>cc, <>mmc_command, <>mmc_device_id, <>switch_type;
	*new {arg assign_type, cc, mmc_command, mmc_device_id, switch_type;
		^super.newCopyArgs(assign_type, cc, mmc_command, mmc_device_id, switch_type);
	}
	asInt8Array {
		^Int8Array[assign_type, cc, mmc_command, mmc_device_id, switch_type];
	}
}
