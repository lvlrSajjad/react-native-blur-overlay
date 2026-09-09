type Listener = () => void;

const listeners = new Map<string, Set<Listener>>();

export function on(event: string, listener: Listener): void {
  const existing = listeners.get(event);

  if (existing) {
    existing.add(listener);
  } else {
    listeners.set(event, new Set([listener]));
  }
}

export function off(event: string, listener: Listener): void {
  const existing = listeners.get(event);

  if (!existing) {
    return;
  }

  existing.delete(listener);

  if (existing.size === 0) {
    listeners.delete(event);
  }
}

export function emit(event: string): boolean {
  const existing = listeners.get(event);

  if (!existing || existing.size === 0) {
    return false;
  }

  // Copy before iterating so listeners can unsubscribe while being called.
  for (const listener of [...existing]) {
    listener();
  }

  return true;
}
