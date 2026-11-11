# TF-IDF and Cosine Similarity

The production implementation is
[`TfidfSimilarityEngine.java`](../../smart-incident-finder/src/main/java/com/incident/finder/engine/TfidfSimilarityEngine.java).
It is a small, corpus-local lexical matcher; it does not use embeddings,
stemming, semantic search, or an external search index.

## Documents and query

For each historical `Incident`, `documentTerms()` tokenizes only:

```java
incident.getTitle() + " " + incident.getDescription()
```

The incident's resolution is intentionally not part of the matching text;
it is returned after a match. The user's incident description is the query.
The engine builds the corpus documents from all loaded incidents on each
request. The current dataset has 95 records.

## Text preprocessing and vocabulary

`tokenize(String)`:

1. Converts text to lowercase with `Locale.ROOT`.
2. Splits on any character outside ASCII letters and digits:
   `[^a-z0-9]+`.
3. Removes empty tokens.

Examples:

```text
"Database---API response 91ms" -> ["database", "api", "response", "91ms"]
"don't"                       -> ["don", "t"]
```

There is **no stop-word removal**. Common words such as “the”, “is”, and
“to” remain tokens when present. There is no stemming, lemmatization,
accent folding, synonym expansion, or language detection. Non-ASCII
characters are separators under the current regex.

The vocabulary is formed from unique terms in the historical title and
description documents. `calculateIdf()` records how many documents contain
each token. Query tokens absent from that vocabulary have no IDF entry and
are ignored while constructing the query vector.

## Term Frequency (TF)

For term \(t\) in document \(d\), the implementation uses normalized
frequency:

\[
TF(t,d)=\frac{\text{count of }t\text{ in }d}{\text{number of tokens in }d}
\]

The same calculation is used for the query. Duplicate words increase the
numerator but also contribute to the total token count. For example, in
`"database database timeout"`, TF(database) is \(2/3\), and TF(timeout) is
\(1/3\). Frequencies are not binary.

## Inverse Document Frequency (IDF)

For a corpus of \(N\) historical documents and a term present in \(df(t)\)
documents, the code uses smoothed IDF:

\[
IDF(t)=\ln\left(\frac{N+1}{df(t)+1}\right)+1
\]

The `+1` smoothing prevents division by zero and keeps IDF positive. A term
appearing in every document receives IDF 1; a rarer term receives a larger
weight.

## TF-IDF vector

Each token weight is:

\[
W(t,d)=TF(t,d)\times IDF(t)
\]

`createVector()` stores these weights in a `Map<String, Double>`. The query
vector uses query term frequencies and the same IDF values calculated from
the historical documents.

## Cosine similarity

For query vector \(q\) and document vector \(d\), the engine computes:

\[
\operatorname{cos}(q,d)=
\frac{\sum_t q_t d_t}
{\sqrt{\sum_t q_t^2}\sqrt{\sum_t d_t^2}}
\]

This measures directional similarity independent of the overall vector
magnitude. Shared weighted terms contribute to the dot product. Terms present
in only one vector do not add to the dot product, but they do affect vector
norms.

### Small numerical example

For illustration only, use two documents:

```text
D1 = "database database timeout"
D2 = "database server"
Q  = "database timeout"
```

There are \(N=2\) documents. `database` appears in both, so
\(IDF(database)=\ln(3/3)+1=1\). `timeout` and `server` each appear in one,
so \(IDF=\ln(3/2)+1\approx1.4055\).

The vectors are approximately:

```text
D1: database = (2/3)*1       = 0.6667
    timeout  = (1/3)*1.4055  = 0.4685
D2: database = (1/2)*1       = 0.5000
    server   = (1/2)*1.4055  = 0.7027
Q:  database = (1/2)*1       = 0.5000
    timeout  = (1/2)*1.4055  = 0.7027
```

Applying cosine gives approximately `cos(Q,D1) = 0.943` (94.3%) and
`cos(Q,D2) = 0.336` (33.6%). The actual application recomputes these values
from all 95 current documents, so scores differ from this two-document
example.

## Ranking, threshold, and displayed percentage

`findTopMatches()`:

1. Returns an empty list for null query, null/empty corpus, no query tokens,
   or a zero-length query vector.
2. Scores each incident and retains only positive-similarity matches.
3. Sorts descending by raw similarity; exact ties sort by ascending incident
   ID.
4. Returns at most three matches.

`IncidentService.searchIncidents()` then filters using the raw score:

\[
100 \times similarity \ge minimumSimilarityPercentage
\]

The default minimum is 10.0%. The service converts accepted values to a
percentage rounded to one decimal place with `RoundingMode.HALF_UP`. The
reported rounded percentage can therefore be near, but not exactly equal to,
the threshold comparison value.

## Unknown words, duplicates, and zero vectors

- **Unknown words:** query words absent from the incident vocabulary have no
  IDF weight and are omitted. If every query word is unknown, the query vector
  is empty and there are no matches.
- **Duplicate words:** term counts are accumulated in a map and normalized
  by total token count. They affect TF; they are not removed from the query.
  IDF document frequency, by contrast, counts a term at most once per
  document using `distinct()`.
- **Empty/zero vectors:** a zero query norm yields no results before document
  scoring. `cosineSimilarity()` also returns 0 if either norm is zero.
- **No positive overlap:** zero-score incidents are not added to the match
  list. A fully unrelated query becomes a successful HTTP 200 with an empty
  `results` array and the no-match message.
- **Invalid numeric values:** the engine rejects non-finite similarity
  calculations; the service validates score range before exposing results.

## What this implementation does not do

The matcher is lexical, so different wording with no shared tokens may not
match even if the meaning is similar. Stop-word removal and normalization
are not implemented. It has no persistent/incremental index: tokenization,
IDF, and vectors are rebuilt per query. Claude can provide separate smart
analysis, but does not calculate or modify the historic match scores.
