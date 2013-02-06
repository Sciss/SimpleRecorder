(
~device = "MOTU 828mk2";
~numInputChannels  = 22;
~numOutputChannels = 20;

s.options.device = ~device;
s.options.numInputBusChannels  = ~numInputChannels;
s.options.numOutputBusChannels = ~numOutputChannels;
s.waitForBoot {
  SynthDef( \beep, { arg out = 0, amp = 0.3, freq = 1000;
	var env = EnvGen.ar( Env([ 0, 1, 1, 0, 0, 1, 1, 0 ], [ 0.01, 0.06, 0.01, 0.06, 0.01, 0.06, 0.01 ]), doneAction: 2 );
	Out.ar( out, SinOsc.ar( freq ) * env * amp );
  }).send( s );
  s.meter.window.alwaysOnTop = true;
};
)

////////////////////////

Synth( \beep )
// x = Synth( \sweep, [ \out, 2 ]);
// ScissUtil.speakerTest( 2, 2, 0.8 );

////////////////////////

(
r = SimpleRecorder( s );
r.makeWindow.alwaysOnTop = true;

~micOffset    = 0; // offset: 0
~micChannels  = 2;
~speakerOffset= 0; // offset: 0
~freqMin      = 20;
~freqMax      = 20000;
~sweepDur     = 24;
~preWait      = 2;
~reverb       = 3;
~amp          = -15.dbamp; // 0.9;
~beep         = true;

c = Bus.audio( s, ~micChannels + 1 );
r.channelOffset 	= c.index; // s.options.numOutputBusChannels + 14;
r.numChannels		= c.numChannels;
r.folder			= "~/Desktop/sweep_rec".standardizePath;
if(File.exists(r.folder).not, { ("mkdir -p " ++ r.folder).unixCmd });
)

////////////////////////

(
f = Routine({ var bndl, synth, name;
	name = "sweep" + ~micChannels;
	SynthDef( name, { arg out = 0, f0 = 20, f1 = 20000, dur = 20, in, aux= 99, amp = 1;
		var freq = Line.ar( f0, f1, dur, doneAction: 2 );
		var sig = SinOsc.ar( freq ) * amp;
		var mic = In.ar( in, ~micChannels );
		Out.ar( out, sig );
		Out.ar( aux, mic.asArray ++ [ sig ]);
	}).send( s );
	r.prepare;
	~preWait.wait;
	if(~beep, {
		Synth( \beep, [ \out, ~speakerOffset, \amp, 0.3 * ~amp ]);
		(~reverb + 1).wait;
	});
	bndl = List.new;
	r.recordToBundle( bndl );
	synth = Synth.basicNew( name, s );
	bndl.add( synth.newMsg( s, [
		\out, ~speakerOffset, \dur, ~sweepDur, \aux, c.index, \in, s.options.numOutputBusChannels + ~micOffset,
	    \f0, ~freqMin, \f1, ~freqMax, \amp, ~amp ]));
	s.listSendBundle( nil, bndl.asArray );
	synth.waitForEnd;
	~reverb.wait;
	r.stop;
	if(~beep, { Synth( \beep, [ \out, ~speakerOffset, \amp, 0.3 * ~amp ])});
	r.revealInFinder;
}).play( SystemClock );
)


// r.revealInFinder;
// s.meter;