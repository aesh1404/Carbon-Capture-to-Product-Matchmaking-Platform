// Guards every <input type="number"> in the app against silent, invisible data corruption.
//
// A focused number input consumes the mouse wheel and steps its own value. So the sequence
// "type 3000 into Price, then scroll down to reach the Submit button" edits the field on the
// way past: eleven wheel notches turned a genuine 3000 into 2989, the form submitted 2989,
// and the backend faithfully stored exactly what it was sent. Nothing downstream can detect
// this - the corrupted value is indistinguishable from one the user meant to type - so it has
// to be stopped at the input.
//
// Blurring on wheel is the fix rather than preventDefault(): React attaches wheel listeners
// passively at the root, so preventDefault() there is ignored (and warns). Once the field
// isn't focused the browser stops treating the wheel as a stepper and the page scrolls
// normally, which is what the user wanted in the first place.
//
// Keyboard arrow-key stepping is deliberately left alone - that one is a real, intended edit.
export function blurOnWheel(event) {
  event.currentTarget.blur()
}
