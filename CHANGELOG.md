# Changelog

## Unreleased

### Fixed
- Draw shorthand Bézier curves correctly

## 0.0.6 - 2025-05-04

### Added

- Basic ImageVector support for [vgo-supported ImageVectors](https://github.com/jzbrooks/vgo/releases/tag/v3.2.0)

## 0.0.5 - 2025-01-26

### Fixed

- Avoid translating coodinates for smooth bézier curves
- Track path fill rules when drawing
- Correctly parse floating point dimensions

## 0.0.4 - 2025-01-20

### Fixed

- Images appear blank or partially drawn if the viewport coordinate system differs from its specified height and width
- `--background-color` arguments can be prefixed with '#' or '0x'

## 0.0.3 - 2025-01-14

### Added

- Build targets for windows and linux systems
- Handle very simple SVGs supported by vgo
  - style tags aren't handled
  - nested clip paths aren't handled
- `--background-color` option (hexadecimal RGBA format)

### Fixed

- Handle group transformations

### Changed

- Default to transparent background

## 0.0.2

### Added

- `--scale` option

### Fixed

- `v` commands were interpreted incorrectly
- smooth bezier curve handling

## 0.0.1

Initial release
