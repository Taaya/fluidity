// Account-free vocabulary sharing: the fragment carries only the parsed list the sender
// entered. It never contains the learner id, library or practice progress.
const sharedWordsFragment = "#share=";
const sharedWordsVersion = "v1";

function createShareLink(words, location) {
  const payload = words.map((text) => String(text)).join("\n");
  return `${location.origin}${location.pathname}${sharedWordsFragment}${sharedWordsVersion}.${encodeURIComponent(payload)}`;
}

function readSharedWords(hash) {
  const fragment = String(hash || "");
  if (!fragment.startsWith(sharedWordsFragment)) {
    return { status: "none", words: [] };
  }

  const value = fragment.slice(sharedWordsFragment.length);
  const separator = value.indexOf(".");
  if (separator === -1 || value.slice(0, separator) !== sharedWordsVersion) {
    return { status: "invalid", words: [] };
  }

  let payload;
  try {
    payload = decodeURIComponent(value.slice(separator + 1));
  } catch {
    return { status: "invalid", words: [] };
  }

  const words = payload
    .split("\n")
    .map((text) => text.replace(/\s+/g, " ").trim())
    .filter(Boolean);
  return words.length ? { status: "ok", words } : { status: "invalid", words: [] };
}

if (typeof module !== "undefined") module.exports = { createShareLink, readSharedWords };
