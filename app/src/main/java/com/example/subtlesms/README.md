# SubtleSMS refactor

## 1. Centralized, lazy-loaded storage — `AppRepository.java`

One singleton (`AppRepository.getInstance(context)`) is now the only place that
talks to the SMS/MMS/Contacts providers. Nothing loads until it's asked for:

- `getConversations(callback)` — returns the cached list instantly if it's
  already been loaded; otherwise queries once and caches the result.
- `getMessages(threadId, address, callback)` — same pattern, cached per thread.
- `resolveContactNames(...)` / `analyzeSentiments(...)` — enrich whatever
  conversation list is already cached, without re-querying messages.
- `refreshConversations(...)` / `refreshMessages(...)` — explicit
  "invalidate + reload," called when a `ContentObserver` fires or right after
  sending a message.
- `appendLocalMessage(...)` — lets `ChatActivity` push a just-sent message
  into the cache immediately (optimistic UI) without a round-trip.
- `markMessageAsAutomated(...)` — the automated-message bookkeeping that used
  to live separately in `ChatActivity` and `SmsWorker` now lives in one place.

Because it's a singleton keyed off `Application` context, `MainActivity`,
`ChatActivity`, and `SmsWorker` all share the same in-memory cache — e.g. once
you've opened a thread, going back to the conversation list and back into the
thread again doesn't re-hit the SMS provider.

Cost of this approach: caches only get invalidated when something tells them
to (a `ContentObserver` callback, or after a send). If you add more write
paths later (e.g. deleting a thread), remember to invalidate the relevant
cache entry there too.

## 2. File breakdown

| File | Responsibility |
|---|---|
| `AppRepository.java` | **New.** All data access + in-memory caching. |
| `SentimentAnalyzer.java` | Renamed from `SentimentAnalysis.java`. Just the scoring rules. |
| `AutoReplyManager.java` | **New.** Decides *whether* and *what* to auto-reply. |
| `AutoReplyScheduler.java` | **New.** Owns the WorkManager enqueue call. |
| `SmsReceiver.java` | Trimmed to: read the incoming SMS, ask `AutoReplyManager`, hand off to `AutoReplyScheduler`. |
| `SmsWorker.java` | Trimmed to: send the SMS, mark it automated via `AppRepository`. |
| `Conversation.java` | **New.** Plain data model, pulled out of `ConversationAdapter.java`. |
| `ConversationAdapter.java` | Adapter only — no querying, no model class inside it anymore. |
| `ChatAdapter.java` | Adapter only (unchanged behavior, just cleaned up). |
| `SmsMessage.java`, `MessageStatus.java` | Unchanged — already single-purpose. |
| `MainActivity.java` | UI glue only: asks `AppRepository` for data, binds the adapter. |
| `ChatActivity.java` | UI glue only: asks `AppRepository` for messages, sends via `SmsManager`, tells the repository about the new message. |

**Delete `SentimentAnalysis.java`** from your project — `SentimentAnalyzer.java`
replaces it. Every other original file has a direct 1:1 replacement above, so
nothing else needs deleting; the old files just weren't organized around a
single responsibility each.

## 3. UI — transparent backgrounds

Kept as-is (these are the "message bubble" styling you said you like, and a
couple of controls that need a fill to read as tappable/visible against the
black background):

- `bg_bubble_sent.xml`, `bg_bubble_received.xml`, `bg_bubble_*_border.xml` —
  the bubble shapes themselves.
- `bg_sentiment_badge.xml` — the small colored sentiment pill; it's a status
  indicator, not general chrome.
- `bg_message_input.xml` — the text field is already a 15%-opacity white
  (`#26FFFFFF`), which reads as "transparent panel" rather than a solid block.
- The FAB's `backgroundTint` — Material FABs need a fill to be legible as a
  button.

Changed to transparent:

- `item_conversation.xml` — the avatar placeholder was a solid filled circle
  (`@color/convoIconBGColor`); it's now a transparent circle with a thin
  stroke in the same tint color, so it reads as an outline icon rather than a
  filled swatch, consistent with the rest of the transparent chrome.

If you also want the sentiment badge or FAB switched to transparent/outlined,
that's a one-line change in each drawable — just say which ones.
