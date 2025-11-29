# EdgeScout: On-Device Agentic RAG for Talent Discovery

   

**EdgeScout** (Repo: `skillvault-kotlin`) is a native Android research prototype that demonstrates **Agentic Retrieval-Augmented Generation (RAG)** entirely on the edge.

It acts as an autonomous "Pocket Recruiter," allowing users to index thousands of resumes, perform semantic searches ("Find a leader with Python skills"), and generate deep profile analyses using a quantized Large Language Model (LLM)—all without a single byte of data leaving the device.

## 🚀 Key Innovation: The "Talent Scout Agent"

Unlike passive keyword search tools, EdgeScout operates as an agentic system with a three-stage cognitive loop:

1.  **Plan (Query Translation)**: Maps abstract intents (e.g., "Creative roles") to high-dimensional vector space.
2.  **Decide (Vector Ranking)**: Autonomously scans a local **ObjectBox** vector store to rank candidates by semantic fit.
3.  **Act (Generative Analysis)**: Uses **Gemma-2B** (Int4 Quantized) to read the candidate's resume and write a "Strengths & Weaknesses" report.

-----

## 📊 Real-World Performance Benchmarks

Validated on **Samsung Galaxy M21 (Exynos 9611)** vs. **High-Performance Simulator**.

### 1\. Retrieval Latency (The "Fast" Loop)

*Dataset: Kaggle Resume Dataset (N=126 to N=2400)*

| Query Complexity | Samsung M21 Latency | Status |
| :--- | :--- | :--- |
| **"Security"** | **146 ms** | 🟢 Real-Time |
| **"Developer"** | **147 ms** | 🟢 Real-Time |
| **"Chef"** (Cold Start) | **204 ms** | 🟡 Acceptable |

> **Result:** The system achieves the research target of **\<200ms** retrieval latency on mid-range hardware.

### 2\. Generative Analysis (The "Deep" Loop)

*Model: Gemma-2B (Int4) via MediaPipe*

| Task | Device | Time to Complete |
| :--- | :--- | :--- |
| **Full Profile Summarization** | AVD / Emulator | \~155 seconds |
| **Full Profile Summarization** | High-End Android | \~45-60 seconds (Est.) |

> **Architecture Note:** Due to the heavy computational cost, Generation is handled as an asynchronous background task ("Report Queue"), ensuring the UI remains responsive.

-----

## 🛠 Technology Stack

  * **Language**: Kotlin 1.9.20 (Coroutines + Flow)
  * **Vector Store**: [ObjectBox 3.7.0](https://objectbox.io/) (NoSQL Edge Database)
  * **Embeddings**: Google MediaPipe Text Embedder (MobileNet v3, 384-dim)
  * **LLM Inference**: **Google MediaPipe LLM API** (running `gemma-2b-it-cpu-int4.bin`)
  * **Architecture**: MVVM with Clean Architecture (Domain/Data/UI layers)
  * **DI**: Hilt 2.48

-----

## 📂 Project Structure

```bash
EdgeScout/
├── app/
│   ├── src/main/assets/
│   │   ├── text_embedder.tflite       # For Vector Search
│   │   └── gemma-2b-it-cpu-int4.bin   # For Generative Analysis (Large File)
│   ├── src/main/java/com/knovik/skillvault/
│   │   ├── domain/
│   │   │   ├── agent/                 # Agentic Logic
│   │   │   │   └── TalentScoutAgent.kt
│   │   │   ├── embedding/             # MediaPipe Embedder
│   │   │   └── llm/                   # Gemma-2B Interface
│   │   ├── data/
│   │   │   └── local/                 # ObjectBox Entities
│   │   └── ui/
│   │       ├── search/                # Semantic Search UI
│   │       └── analysis/              # Generative Report UI
```

-----

## ⚡ Setup & Installation

**Prerequisites:**

  * Android Studio Iguana or later
  * Device with **4GB+ RAM** (required for LLM inference)
  * Android API 26+

### 1\. Clone & Sync

```bash
git clone https://github.com/madusankapremaratne/skillvault-kotlin.git
cd skillvault-kotlin
```

### 2\. Download the Models

1.  **Embedder:** Download `text_embedder.tflite` from [MediaPipe Tasks](https://www.google.com/search?q=https://developers.google.com/mediapipe/solutions/text/text_embedder).
2.  **LLM:** Download `gemma-2b-it-cpu-int4.bin` from [Kaggle Models](https://www.kaggle.com/models/google/gemma).

### 3\. Deploying the LLM (Important)

Since `gemma-2b` is large (\~1.3GB), it is not included in the repo. We recommend pushing it directly to the device storage to avoid slow build times.

**Step 1: Push the model via ADB**
Run this command in your terminal to copy the model to the app's data directory:

```bash
# MacOS / Linux / Windows PowerShell
adb push gemma-2b-it-cpu-int4.bin /storage/emulated/0/Android/data/com.knovik.skillvault/files/
```

**Step 2: Update Code to Read from Storage**
In `TalentScoutAgent.kt`, ensure you are loading the model from the correct path:

```kotlin
val modelPath = context.getExternalFilesDir(null)?.absolutePath + "/gemma-2b-it-cpu-int4.bin"
val modelFile = File(modelPath)

if (modelFile.exists()) {
    // Load from device storage (Fast Dev Cycle)
    llmInference = LlmInference.createFromOptions(context, 
        LlmInference.Options.builder()
            .setModelPath(modelPath)
            .build())
} else {
    // Fallback to Assets (Production Build)
    llmInference = LlmInference.createFromOptions(context, 
        LlmInference.Options.builder()
            .setModelPath("gemma-2b-it-cpu-int4.bin") 
            .build())
}
```

### 4\. Build & Run

```bash
./gradlew installDebug
```

-----

## 📖 Usage Workflow

### 1\. Ingestion (The Knowledge Base)

Import the Kaggle Resume CSV. The app automatically chunks text and generates 384-dimensional vectors stored in ObjectBox.

### 2\. Semantic Search (The Scout)

Type a natural language query: *"Find me a project manager who knows Agile and Python."*

  * **Under the hood:** The agent converts your query to a vector and performs a Cosine Similarity search.
  * **Output:** Ranked list of candidates with "Fit Scores."

### 3\. Deep Analysis (The Analyst)

Click the **"AI Analyze"** button on a candidate profile.

  * **Under the hood:** The app loads Gemma-2B into RAM. It feeds the resume text into a prompt: *"Analyze this resume for a [Role]. List strengths and missing skills."*
  * **Output:** A generated text report appears after processing.

-----

## 🔬 Research Context

This project was developed as part of the research paper:
**"Benchmarking On-Device Agentic RAG for Privacy-Preserving Talent Discovery"**

  * **Problem:** Cloud LLMs violate GDPR when processing resumes.
  * **Solution:** Move the entire "Recruiter Brain" (Indexing + Retrieval + Reasoning) to the Edge.
  * **Dataset:** [Kaggle Resume Dataset](https://www.kaggle.com/datasets/saugataroyarghya/resume-dataset)

## License

MIT License.

**Project Lead**: Madusanka Premaratne Rathnayake Mudiyanselage
**Contact**: [rmmpremaratne@gmail.com]
**GitHub Issues**: [https://github.com/madusankapremaratne/skillvault-kotlin/issues](https://github.com/madusankapremaratne/skillvault-kotlin/issues)
