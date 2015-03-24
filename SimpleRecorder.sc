/**
 *	(C)opyright 2006-2015 Hanns Holger Rutz.
 *	Placed in the public domain.
 *
 *	Class dependancies: none
 *
 *	Basically a copy-and-paste from Server and ServerPlusGUI
 *
 *	Changelog:
 *		30-Jun-06	  added setTarget
 *		15-Aug-07	  added support for OSCBundle
 *		25-Jun-08	  using thisProcess.platform.recordingsDir, and +/+
 *      08-Jul-12     bits of cleanup. remove TypeSafe dependency
 *      24-Mar-15     fixed tryChanged dependency; now revealOnDesktop works for Linux
 *
 *	@version	0.18.0, 24-Mar-15
 *	@author	Hanns Holger Rutz
 *
 *	@todo	peak meter
 *	@todo	timer task : should handle pause / resume
 *	@todo	isRecording method
 */
SimpleRecorder {
	var <server;
	var buf, <node, <>headerFormat = "aiff", <>sampleFormat = "float";
	var <channelOffset		= 0;
	var <numChannels		= 2;
	var <>folder;
	var window;
	var <isPrepared		= false;
	var recentPath		= nil;

	var target;
	var targetAddAction	= \addToTail;

	classvar headerSuffix;

	*initClass {
		headerSuffix		= IdentityDictionary.new;
		headerSuffix.put( \aiff, "aif" );
		headerSuffix.put( \next, "au" );
		headerSuffix.put( \wav, "wav" );
		headerSuffix.put( \wave, "wav" );
		headerSuffix.put( \ircam, "irc" );
		headerSuffix.put( \w64, "w64" );
		headerSuffix.put( \caf, "caf" );
		headerSuffix.put( \raw, "raw" );
	}

	*new { arg server;
		^super.new.prInitRecorder( server );
	}

	*prMethodWarn { arg method, message;
		(method.ownerClass.name ++ ":" ++ method.name ++ " : " ++ message).warn;
	}

	*prMethodError { arg method, message;
		(method.ownerClass.name ++ ":" ++ method.name ++ " failed: " ++ message).error;
	}

	prInitRecorder { arg argServer;
		server	= server ?? Server.default;
		folder	= thisProcess.platform.recordingsDir;
	}

	revealOnDesktop {
		var path, cmd;

		if( recentPath.notNil, {
			path = PathName( recentPath );
			cmd = if (thisProcess.platform.name === \linux, {
				"xdg-open \"" ++ path.pathOnly ++ "\""; // not possible to select file, yet
			}, {
				"osascript -e 'tell application \"Finder\"' -e activate -e 'open location \"file:\/\/" ++ path.pathOnly ++
					"\"' -e 'select file \"" ++ path.fileName ++ "\" of folder of the front window' -e 'end tell'";
			});
			cmd.postln;
			cmd.systemCmd;
		}, {
			SimpleRecorder.prMethodError( thisMethod, "Soundfile has not yet been specified" );
		});
	}

	channelOffset_ { arg off;
		channelOffset = off;
		this.tryChanged( \channelOffset );
	}

	numChannels_ { arg num;
		numChannels = num;
		this.tryChanged( \numChannels );
	}

	setTarget { arg group, addAction = \addToTail;
		target			= group;
		targetAddAction	= addAction;

		if( node.notNil, {
			case { addAction === \addToTail }
			{
				node.moveToTail( target );
			}
			{ addAction === \addToHead }
			{
				node.moveToHead( target );
			}
			{ addAction === \addBefore }
			{
				node.moveBefore( target );
			}
			{ addAction === \addAfter }
			{
				node.moveAfter( target );
			};
		});
	}

	record {
		if( buf.isNil, {
			SimpleRecorder.prMethodWarn( thisMethod, "Please execute prepareForRecord before recording" );
		}, {
			if( node.isNil, {
				node = Synth( "simpleRecorder" ++ buf.numChannels,
					[ \i_buf,  buf.bufnum, \i_bus, channelOffset ], target ?? { RootNode( server )}, targetAddAction );
				this.tryChanged( \started );
			}, {
				node.run( true );
				this.tryChanged( \resumed );
			});
			"Recording".postln;
		});
	}

	recordToBundle { arg bundle;
		node = Synth.basicNew( "simpleRecorder" ++ buf.numChannels, server );
		bundle.add( node.newMsg( target ?? { RootNode( server )},
			[ \i_buf,  buf.bufnum, \i_bus, channelOffset ], targetAddAction ));
	}

	prepareAndRecord { arg path;
		fork {
			if( this.prepare( path ), {
				server.sync;
				this.record;
			});
		};
	}

	prepareAndRecordToBundle { arg bundle, path;
		this.prepareToBundle( bundle, path );
		this.recordToBundle( bundle );
	}

	pause {
		if( node.notNil, {
			node.run( false );
			"Paused".postln;
			this.tryChanged( \paused );
		}, {
			"Not Recording".warn;
		});
	}

	stop {
		if( node.notNil, {
			node.free;
			node = nil;
			"Recording Stopped".postln;
		});
		if( buf.notNil, {
			buf.close({ arg buf; buf.free; });
			buf = nil;
			isPrepared = false;
			this.tryChanged( \stopped );
		}, {
			"Not Recording".warn;
		});
	}

	prepare { arg path;
		var bundle = OSCBundle.new;
		if( this.prepareToBundle( bundle, path ), {
			CmdPeriod.add( this );
			isPrepared = true;
			this.tryChanged( \prepared );
			bundle.send( server );
			^true;
		}, {
			^false;
		});
	}

	prepareToBundle { arg bundle, path;
		var def;
		if( path.isNil, {
			if (File.exists(folder).not, { File.mkdir(folder) });
			path = folder +/+ "SC_" ++ Date.localtime.stamp ++ "." ++ headerSuffix[ headerFormat.asSymbol ];
		});
		if( isPrepared, {
			SimpleRecorder.prMethodError( thisMethod, "Already armed or recording" );
			^false;
		}, {
			if( server.serverRunning, {
				recentPath = path;
				buf = Buffer( server, 65536, numChannels );
//
//				buf = Buffer.alloc( server, 65536, numChannels, { arg buf;
//						buf.writeMsg( path, headerFormat, sampleFormat, 0, 0, true ); });
				if( buf.notNil, {
					bundle.addPrepare( buf.allocMsg );
					bundle.addPrepare( buf.writeMsg( path, headerFormat, sampleFormat, 0, 0, true ));
					def = SynthDef( "simpleRecorder" ++ numChannels, { arg i_bus, i_buf;
						DiskOut.ar( i_buf, In.ar( i_bus, numChannels ));
					});
					bundle.addPrepare([ "/d_recv", def.asBytes ]);
					^true;
				}, {
					SimpleRecorder.prMethodError( thisMethod, "Cannot allocate buffer" );
					^false;
				});
			}, {
				SimpleRecorder.prMethodError( thisMethod, "Server not running" );
				^false;
			});
		});
	}

	cmdPeriod {
		if( node.notNil, {
			node = nil;
		});
		if( buf.notNil, {
			buf.close({ arg buf; buf.free; });
			buf = nil;
		});
		isPrepared = false;
		this.tryChanged( \cmdPeriod );
		CmdPeriod.remove( this );
	}

	makeWindow { arg w;
		var ggRec, ggChannelOffset, ggTimer, ggNumChannels, serverRunning, serverStopped, ctlr, ctlr2,
		    recTimerTask, recTimerFunc;

		if( window.notNil, { ^window.front });

		if( w.isNil, {
			w = window		= Window( "Recorder for Server '" ++ server.name.asString ++ "'",
								Rect( 10, Window.screenBounds.height - 96, 340, 46 ), resizable: false );
			w.view.decorator	= FlowLayout( w.view.bounds );
		});

		ggRec = Button( w, Rect( 0, 0, 72, 24 ))
			.states_([
				[ "prepare",  Color.black, Color.clear ],
				[ "record >", Color.red,   Color.gray( 0.1 )],
				[ "stop []",  Color.black, Color.red ]
			])
//			.value_( if( node.isNil, { if( isPrepared, 1, 0 )}, 2 ))
			.action_({ arg b;
				case { b.value == 1 }
				{
					this.prepare;
				}
				{ b.value == 2 }
				{
					this.record;
				}
				{
					this.stop;
				};
			});

		w.view.decorator.shift( 4, 0 );

		ggTimer = StaticText( w, Rect( 0, 0, 72, 24 ))
			.string_( "00:00:00" );

		StaticText( w, Rect( 0, 0, 24, 24 ))
			.align_( \right )
			.string_( "Bus" );

		ggChannelOffset = NumberBox( w, Rect( 0, 0, 36, 24 ))
			.align_( \right )
			.object_( channelOffset )
			.action_({ arg b;
				this.channelOffset_( b.value.asInteger );
			});

		StaticText( w, Rect( 0, 0, 48, 24 ))
			.align_( \right )
			.string_( "Chans" );

		ggNumChannels = NumberBox( w, Rect( 0, 0, 36, 24 ))
			.align_( \right )
			.object_( numChannels )
			.action_({ arg b;
				this.numChannels_( b.value.asInteger );
			});

		serverRunning = {
			this.prInAppClock({
				ggRec.enabled = true;
			});
		};

		serverStopped = {
			recTimerTask.stop;
			this.prInAppClock({
				ggRec.value = 0;
				ggRec.enabled = false;
				ggChannelOffset.enabled = true;
				ggNumChannels.enabled = true;
			});
		};

		if( server.serverRunning, serverRunning, serverStopped );

		recTimerFunc = {
			var str, startTime, t, oldT;
//			startTime		= Main.elapsedTime;
//			oldT			= 0;
			inf.do({ arg t;
//				t	= Main.elapsedTime - startTime;
//				if( t != oldT, {
					str	= ((t.div( 3600 ) % 60 * 100 + (t.div( 60 ) % 60)) * 100 + (t % 60) + 1000000).asString;
					{
						ggTimer.string = str.copyRange( 1, 2 ) ++
							":" ++ str.copyRange( 3, 4 ) ++ ":" ++ str.copyRange( 5, 6 );
					}.defer;
//					oldT	= t;
//				});
//				0.1.wait;
				1.0.wait;
			});
		};
		recTimerTask = Task( recTimerFunc, SystemClock );

		ctlr = SimpleController( server )
			.put( \serverRunning, {
				if( server.serverRunning, serverRunning, serverStopped );
			});

		ctlr2 = SimpleController( this )
			.put( \prepared, {
				this.prInAppClock({
					ggRec.value = 1;
					ggChannelOffset.enabled = false;
					ggNumChannels.enabled = false;
				});
			})
			.put( \started, {
				recTimerTask.reset;
				recTimerTask.start;
				this.prInAppClock({
					ggRec.setProperty( \value, 2 );
				});
			})
			.put( \stopped, {
				recTimerTask.stop;
				this.prInAppClock({
					ggRec.setProperty( \value, 0 );
					ggChannelOffset.enabled = true;
					ggNumChannels.enabled = true;
				});
			})
			.put( \paused, {
				recTimerTask.pause;
				this.prInAppClock({
					ggRec.value = 1;
				});
			})
			.put( \resumed, {
				recTimerTask.resume;
				this.prInAppClock({
					ggRec.value = 2;
				});
			})
			.put( \channelOffset, {
				this.prInAppClock({
					ggChannelOffset.object = channelOffset;
				});
			})
			.put( \numChannels, {
				this.prInAppClock({
					ggNumChannels.object = numChannels;
				});
			})
			.put( \cmdPeriod, {
				serverStopped.value;
				recTimerTask = Task( recTimerFunc, SystemClock );
				if( server.serverRunning, serverRunning );
			});

		if( isPrepared, {
			if( node.isNil, {
				ctlr2.update( this, \prepared );
			}, {
				ctlr2.update( this, \started );
			});
		});

		w.onClose = {
			window = nil;
			recTimerTask.stop;
			ctlr.remove;
			ctlr2.remove;
		};

		w.front;
		^w;
	}

	 prInAppClock { arg func;
	 	if( this.canCallOS, func, { func.defer });
	 }

	tryChanged { arg ... args;
		dependantsDictionary.at( this ).copy.do({ arg item;
			try {
				item.update( this, *args );
			} { arg e;
				e.reportError;
			};
		});
	}
}