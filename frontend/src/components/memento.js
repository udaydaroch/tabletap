/**
 * Memento pattern for undo / redo in the floor-plan designer.
 *
 *   FloorPlanMemento   – a saved snapshot of the floor plan (plus a label like "Move 1").
 *                        Its contents are hidden: only the originator can read them back.
 *   FloorPlanOriginator – owns the live floor plan. It creates mementos of the current plan
 *                        and restores the plan from a memento.
 *   UndoCaretaker      – keeps the undo and redo stacks of mementos. It never looks inside them;
 *                        it only decides which one to hand back to the originator.
 *
 * The editor treats the floor plan as immutable (every edit produces a new object), so a memento can
 * hold a reference to the plan instead of a deep copy — cheap, and the saved state can never change.
 */

// memento -> saved floor plan. Module-private, so nothing outside this file can read a memento's contents.
const savedState = new WeakMap();

export class FloorPlanMemento {
  constructor(label, state) {
    this.label = label;
    savedState.set(this, state);
    Object.freeze(this);
  }
}

export class FloorPlanOriginator {
  /** @param getState returns the current floor plan; @param setState replaces it */
  constructor(getState, setState) {
    this.getState = getState;
    this.setState = setState;
  }

  /** Capture the current floor plan. */
  save(label) {
    return new FloorPlanMemento(label, this.getState());
  }

  /** Put the floor plan back to what the memento captured. */
  restore(memento) {
    this.setState(savedState.get(memento));
  }

  /** True if the plan hasn't changed since the memento was taken (e.g. a click without a drag). */
  isUnchangedSince(memento) {
    return savedState.get(memento) === this.getState();
  }
}

export class UndoCaretaker {
  constructor(limit = 100) {
    this.limit = limit;
    this.undoStack = [];
    this.redoStack = [];
  }

  /** Remember the state from just before an edit. A new edit makes the redo history invalid. */
  checkpoint(memento) {
    this.undoStack.push(memento);
    if (this.undoStack.length > this.limit) this.undoStack.shift();
    this.redoStack = [];
  }

  undo(originator) {
    const previous = this.undoStack.pop();
    if (!previous) return false;
    this.redoStack.push(originator.save(previous.label)); // so redo can come back to "now"
    originator.restore(previous);
    return true;
  }

  redo(originator) {
    const next = this.redoStack.pop();
    if (!next) return false;
    this.undoStack.push(originator.save(next.label));
    originator.restore(next);
    return true;
  }

  clear() {
    this.undoStack = [];
    this.redoStack = [];
  }

  get undoLabel() { return this.undoStack.at(-1)?.label ?? null; }

  get redoLabel() { return this.redoStack.at(-1)?.label ?? null; }
}
