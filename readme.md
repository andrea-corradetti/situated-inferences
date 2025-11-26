# SIA: Situated Inferences Add-on for GraphDB

**SIA (Situated Inferences Add-on)** is a Kotlin-based plugin for [GraphDB](https://graphdb.ontotext.com/) designed to enable reasoning on **situated datasets**. It addresses complexities in Semantic Web reasoning where assertions are made about other assertions (e.g., beliefs, provenance, temporal validity) by localizing inferences to specific contexts.

This project implements the concepts discussed in the Bachelor's thesis _"Inferenze Situate: Un'implementazione Per GraphDB"_ by Andrea Corradetti (University of Bologna, 2023/2024).

## ⚠️ Status: Experimental & Incomplete

**This software is a research proof-of-concept and is NOT production-ready.**

Please be aware of the following critical limitations identified during the thesis evaluation:

- **Unstable Query Execution:** The plugin relies on specific SPARQL pattern evaluation orders. GraphDB's internal query optimizer often reorders these patterns, which frequently breaks the plugin's logic during execution.
- **Broken Features:** The automatic conversion for RDF-Star and Reification is currently buggy and does not reliably generate the expected situated contexts.
- **Performance:** The forward-chaining strategy implemented at query time introduces significant performance overhead and is not scalable for large datasets.
- **Usage:** This codebase serves primarily as a demonstration of the _concepts_ discussed in the thesis, rather than a usable tool.

## 🧠 The Problem: The Superman Paradox

Standard reasoning engines (like OWL) often fail when dealing with situated assertions (assertions about assertions). A classic example is the **Superman Paradox**:

1. **Premise:** Superman is identical to Clark Kent (`owl:sameAs`).
2. **Premise:** Lois Lane believes Superman can fly.
3. **Incorrect Inference:** Lois Lane believes Clark Kent can fly.

While logically sound under standard substitution rules, this inference is semantically incorrect in the context of Lois Lane's beliefs (she doesn't know they are the same person). SIA solves this by treating contexts (beliefs, timeframes, etc.) as **referentially opaque**, preventing global truths from leaking into local contexts unless explicitly allowed.

## ✨ Key Features

- **Context-Localized Inference:** Performs reasoning within isolated "situated contexts" (Named Graphs) without polluting the global default graph.
- **Multi-Format Support:** Handles situated data expressed in:
  - **RDF-Star** (Quoted Triples)
  - **Standard Reification**
  - **Named Graphs**
- **Disagreement Detection:** Identifies logical inconsistencies and disagreements between different contexts (e.g., conflicting viewpoints).
- **Customizable Permeation:** Allows specific "Shared Knowledge" graphs to permeate into local contexts while keeping the rest isolated.
- **Forward-Chaining on Query:** Inferences are calculated at query time using a forward-chaining strategy, ensuring results are up-to-date without permanently materializing them in the store.

## 🛠️ Installation

### Prerequisites

- **GraphDB** (Tested on versions compatible with the plugin API)
- **Java/Kotlin** environment for building.

### Build from Source

1. Clone the repository:

   ```bash
   git clone [https://github.com/andrea-corradetti/situated-inferences.git](https://github.com/andrea-corradetti/situated-inferences.git)
   cd situated-inferences
   ```

2. Build the plugin using Gradle:

   ```bash
   ./gradlew assemble
   ```

   _Note: Use `./gradlew build` to run tests._

3. Locate the generated JAR artifact in the `build/libs` directory.

### Installation in GraphDB

1. Stop your GraphDB instance.
2. Copy the generated JAR file into the GraphDB plugins directory:
   ```bash
   cp build/libs/situated-inferences-addon-X.X.jar $GRAPHDB_HOME/lib/plugins/situated-inferences/
   ```
3. Restart GraphDB.

## ⚙️ Configuration

**Crucial Step:** To ensure SIA functions correctly, you must configure your GraphDB repository specifically to avoid conflicts with default reasoners.

1. **Disable Consistency Checks:** SIA handles disagreements internally. GraphDB's global consistency checks must be **OFF**.
2. **Disable `owl:sameAs` Optimization:** GraphDB's hard-coded `owl:sameAs` handling interferes with context isolation.
3. **Use Custom Ruleset:**
   - Select the custom ruleset provided with this project: `builtin_owl2-rl_renamed.pie`.
   - This ruleset renames standard rules to bypass GraphDB's internal optimizations that ignore context.

## 🚀 Usage

SIA uses special IRIs and SPARQL patterns to define which graphs act as **Situated Contexts** and which act as **Shared Knowledge**.

### 1. Defining Contexts

You can define a schema graph (e.g., `schemas:thoughts`) to categorize your named graphs:

```sparql
PREFIX conj: <[https://w3id.org/conjectures/>](https://w3id.org/conjectures/>);
PREFIX schemas: <[https://w3id.org/conjectures/schemas/>](https://w3id.org/conjectures/schemas/>);
PREFIX : <[http://example.org/>](http://example.org/>);

INSERT DATA {
    GRAPH schemas:thoughts {
        # Define a graph as shared knowledge (available to all contexts)
        conj:sharedKnowledge a conj:SharedKnowledgeContext.

        # Define specific named graphs as isolated situated contexts
        :LoisLanesThoughts a conj:SituatedContext.
        :MarthaKentsThoughts a conj:SituatedContext.
    }
}
```

### 2. Querying with Situated Inferences

To perform inferences, use the `conj:situations` magic graph in your `FROM` clause and bind the schema task in your query.

```sparql
PREFIX conj: <[https://w3id.org/conjectures/>](https://w3id.org/conjectures/>);
PREFIX schemas: <[https://w3id.org/conjectures/schemas/>](https://w3id.org/conjectures/schemas/>);
PREFIX : <[http://example.org/>](http://example.org/>);

SELECT ?s ?p ?o ?context
FROM conj:situations
WHERE {
    # Activate the plugin for the specific schema
    ?task conj:situateSchema schemas:thoughts.

    # Bind the context variable to a specific situated graph
    ?task conj:hasSituatedContext ?context.

    # Query the context (results will include inferred triples)
    GRAPH ?context {
        ?s ?p ?o .
    }
}
```

### 3. Handling RDF-Star & Reification

SIA can automatically convert RDF-Star quoted triples and standard reification into situated named graphs for reasoning.

**Example (RDF-Star):**

```turtle
# Input Data
:LoisLane :thinks << :Superman :can :fly >> .
```

SIA expands this into a context (e.g., `:LoisLaneThoughts`) containing `:Superman :can :fly`, performs reasoning within that context, and allows you to query the results.

## 🤝 Contributing

This project is an experimental implementation. Issues regarding SPARQL query reordering and RDF-Star conversion stability are known (see Thesis Chapter 5). Contributions to improve the `Pattern Interpretation` logic are welcome.

## 📚 Reference

Based on the Bachelor's Thesis: _Inferenze Situate: Un'implementazione Per GraphDB_, University of Bologna, 2023/2024.
