# SIO - A variant of the MCxxxx stack programming language from Shenzhen I/O™

This implementation is a runtime / VM / interpreter that parses and runs an extension of the stack programming language
featured in [Zachtronic's Shenzhen I/O](https://www.zachtronics.com/shenzhen-io/).

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
- Declare your own registers at the beginning of a file by writing `$name`. Use these to store values. In the future the language will hopefully
  feature XBus and simple I/O registers for passing values between multiple nodes, so the prefixes `x`and `p` should be
  for those types of registers only.
- Use the new built-in registers!
  - Set a clock-speed by writing to the `clk`-register. Write `-1` to enable overclocking (makes it go faster by disabling
    timing entirely)
  - Read from `stdin` by requesting `n` characters by `mov`ing to the `stdin` register.
    Then, you can access the values by `mov`ing from the register.
  - Write to `stdout` by `mov`ing to the `stdout` register.
  - Write to `stderr` by `mov`ing to the `stderr` register.
  - Write to `rng` to seed a random generator. `mov` from the register to get a value! 
    Try different types to get different results!
- Cast values between the types using the `cst T` instruction. It operates on the `acc` register.
- End your program with the new `end` instruction.

Unfortunately a lot of the documentation was in Italian(???) so I don't really know how everything works yet. 
