// A simple audio recorder for SuperCollider.
//
// (C)opyright 2006-2015 by Hanns Holger Rutz.
//
// Permission is hereby granted to do whatever
// you wish with the source code. It comes
// without any liability or warranty.
// Use at your own risk.

// creata a recorder instance
r = SimpleRecorder.new;

// show GUI controls
// (works with all GUI kits)
r.makeWindow;

// specify target folder
r.folder = "~/Desktop/".standardizePath;

// specify bus and number of channels
r.channelOffset = 0;
r.numChannels   = 4;

// specify audio file format
r.headerFormat = \w64;    // \aiff, \next, \wav, \ircam, \w64, \caf, \raw
r.sampleFormat = \int24;  // \int16, \int24, \float

// create some signal to record
// (
s.waitForBoot {
// analog bubbles + lfo pulse wave
x = { var f, g, h;
	f = LFSaw.kr(0.4, 0, 24, LFSaw.kr([8,7.23], 0, 3, 80)).midicps; // glissando function
	g = CombN.ar(SinOsc.ar(f, 0, 0.04), 0.2, 0.2, 4); // echoing sine wave
	h = CombL.ar(
		RLPF.ar(LFPulse.ar(FSinOsc.kr(0.05,0,80,160),0,0.4,0.05),
		   FSinOsc.kr([0.6,0.7],0,3600,4000), 0.2),
		0.3, [0.2,0.25], 2);
	g.asArray ++ h.asArray;
}.play;
}
// )

// prepare buffer and start recording
// (if you provide an argument, that's the full path name of
//   the file to record; otherwise an automatic name will be
//   picked and appended to the `folder` setting)
r.prepareAndRecord;

// then after a while...
x.free;
r.stop;

// show recorded file in the Finder (OS X) or folder on desktop (Linux)
r.revealOnDesktop;

/////////////////////////////////////////////
// other functionality

y = Group.tail(s);
r.setTarget(y);  // recording synth will be here

// prepareAndRecordToBundle --
// if you want to start recording sample accurately with other synths starting

r.node;  // current recording synth, nil if none
