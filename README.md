## vat

vat renders vector graphics (SVG, Compose ImageVectors, & Android Vector Drawables) to your terminal.

Any terminal that implements the kitty graphics protocol should work (ghostty, kitty, wezterm, etc)

## Installation

#### Homebrew
`brew install jzbrooks/repo/vat`

#### Manually
Download the distribution from the releases page and ensure it has execute permission. Give it execute permissions e.g. `chmod u+x vat`.

## Example

<img width="798" alt="Screenshot 2025-01-07 at 9 26 30 PM" src="https://github.com/user-attachments/assets/10345d73-50ca-4d45-b982-e459914d6ef9" />

The terminal is [ghostty](http://ghostty.org).

The image can be found at http://plurib.us/1shot/2008/eleven_below/.

## Command Line Interface

```
> vat [options] [file]

vat renders vector artwork (SVG & Android Vector Drawable) to the terminal.

Options:
  --background-color   background color in hexadecimal RGBA format
  -s --scale           scale factor (float or integer)
  -h --help            print this message
  -v --version         print the version number
```

> `java -jar vat` for Windows

## Build instructions

This project uses the Gradle build system.

To build the binary: `./gradlew binary`

To see all available tasks: `./gradlew tasks`

