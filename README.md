# SIO - A variant of the MCxxxx programming language from Shenzhen I/O™

This implementation is a runtime / VM / interpreter that parses and runs an extension of the programming language
featured in [Zachtronics' Shenzhen I/O](https://www.zachtronics.com/shenzhen-io/).

## Example

Write your code in a `.sio` file:
```
$x0 # What's this?
# - It's a way to declare your own registers, if you need them.

#run this once
@mov 100 acc
mov acc stdout # stdout? What's that?
# - stdout is a register that you can use to write to stdout :)
# What does it write?
# - whatever you give it. It can be string or it can be binary data. 
#   It's just like stdout.

sub 1

mov "\n" stdout # A string literal?
# - exactly!

tlt acc 1
+ mov "done\n" stdout
+ end # I can guess what this instruction does...
# - yeah.
slp 1

```

## Motivation

I was looking for something novel that I can implement my solutions for the next advent of code in. 
I wanted to work with something as annoying and crazy as Shenzhen I/O!
Luckily, I found this kotlin project in a dumpster outside a chinese-themed grill.

## New Features

- Dynamically typed values (yes, the language is now worse), *store types such as integers, floating point numbers and char strings!* wow
  - Cast values between the types using the `cst T` instruction. It operates on the `acc` register.
  - All the `acc`-related instructions such as `acc`, `add`, `sub` & `mul` apply to all the types, try them out!
  - Test-instructions are also applicable.
- Declare your own registers at the beginning of a file by writing `$name`. Use these to store values. XBus and simple I/O registers are used for passing values between multiple nodes, so the prefixes `x`and `p` should be for those types of registers only. You still have to declare them and the order of the declarations determine the identity of each port.
- Memory! Declare sequences of memory as special registers. 
  - Comes in a sized variant (`*name[10]` makes a memory register, 10 values long) and an unsized, infinite, variant (`*name`).
  - Change the element referenced by `mov`'ing to the accompanying `&`-register, (`&name` for both previous examples). 
- Use the new built-in registers!
  - Set a clock-speed in Hz by `mov`ing to the `clk`-register. `mov -1 clk` to enable overclocking (makes it go faster🔥🔥🔥)
  - Read from `stdin` by requesting `n` characters by `mov`ing to the `stdin` register.
    Then, you can access the values by `mov`ing from the register.
  - Write to `stdout` by `mov`ing to the `stdout` register. Read back what you wrote by moving *from* stdout (???)
  - Write to `stderr` by `mov`ing to the `stderr` register.
  - Write to `rng` to seed a random generator. `mov` from the register to get a value! 
    Try different types to get different results!
  - Use the `xsz`, `ysz`, `gfx` and `pxl` registers to render simple raster graphics.
- End your program with the new `end` instruction.

Unfortunately a lot of the documentation was in Zealandic(???) so I don't really know how everything works yet. 

## Using the program

First, download a release from the releases page. The project is in Kotlin, so it compiles to a .jar file.
You need at least Java 8 to run it. 

Once you have it, you can run it like:

`java -jar sio.jar <your .sio script>`

There's also a native version for `amd64` linux. This has been compiled with Graalvm so maybe it's faster?
However, It's uncertain if graphical sio applications work with the native build.

## Enabling graphics

![SIO's `gfx` register in action](drawing.gif)

I found a raster-graphics-extension for SIO on a now-defunct meme forum, 
one of the users were using it as the background for the avatar.
It's interesting to play with, so I've merged it into the existing code. 
It's using the state-of-the-art Java Abstract Window Toolkit (AWT) to render graphics, 
so it should be available for graphics-enabled Java installations.

[I wrote an example application (viewed above).](src/test/resources/drawing.sio)

## Other examples

Check out the [test-resources folder](src/test/resources/) for more examples used in testing.

## Don't like it?

Press backspace on your keyboard to go back in your browser-history. If that doesn't work, you are probably forced to 
either type a new URL into the URL-field at the top or close the tab. If *that* doesn't work, you can press CTRL+W to close the tab.
In the most dire cases you can probably get away from this page by pressing ALT+F4, but only do this if you are 
prepared to close the entire browser. 

If that doesn't work for you, maybe you can leave the page open until the next time you restart or something.
I've added some tasteful art so that the window at least stays decorative.

![Tasteful art](./tasteful_art.jpg)
