# NebulaPulsar

NebulaPulsar is a proof-of-concept in-memory implant framework for Java (JSP) and ASP.NET (ASPX/ASHX/ASMX), originally developed as part of the Alien project.

Unlike traditional webshell demonstrations that focus solely on command execution, NebulaPulsar explores how an in-memory implant can establish an encrypted communication channel, dynamically load payloads, and execute them entirely in memory.

The project is intended for security research, malware analysis, and defensive education. It is not designed to be an operational offensive framework.

Technical details: [NebulaPulsar: A Proof-of-Concept In-Memory Implant Framework for JSP and ASP.NET](https://iss4cf0ng.github.io/2026/06/27/2026-6-27-NebulaPulsar/)

<p align="center">
    <img src="https://iss4cf0ng.github.io/images/article/2026-6-27-NebulaPulsar/6.png" width=700/>
</p>

---

## Disclaimer

> *"If you shame attack research, you misjudge its contribution. Offense and defense aren't peers. Defense is offense's child."*  — John Lambert

This project was created **solely for educational, research, and defensive cybersecurity purposes**.

Its purpose is to study the implementation of modern in-memory implants, encrypted communication, and dynamic payload execution across Java and .NET platforms.

The project is **not intended for unauthorized access, persistence, or malicious deployment**. Any use against systems without explicit authorization is strictly discouraged.

The author assumes **no responsibility** for any misuse or damage resulting from this project.

---

## Motivation

While developing **[Alien](https://github.com/iss4cf0ng/Alien)**, I wanted to better understand how modern memory-resident webshells and implants are designed internally.

Many publicly available webshells focus on functionality but rarely explain *why* they are implemented that way. Features such as encrypted communication, in-memory payload execution, dynamic class loading, and session-based implants are often treated as black boxes.

NebulaPulsar was created to bridge that gap.

Rather than cloning an existing implementation, this project explores the underlying concepts behind modern memory implants and demonstrates how those ideas can be implemented on both the Java and .NET platforms.

Throughout the development process, I studied topics including:

- Java `ClassLoader` internals
- .NET `Assembly.Load()` and reflection
- In-memory payload execution
- Encrypted communication channels
- Session-based memory implants
- Dynamic payload loading
- Basic anti-forensics techniques

The goal is to understand **how these techniques work**, not to encourage their misuse.

---

## Demonstration (Screenshot)

### JSP

<p align="center">
    <img src="https://iss4cf0ng.github.io/images/article/2026-6-27-NebulaPulsar/7.png" width=700/>
</p>

---

### ASPX

<p align="center">
    <img src="https://iss4cf0ng.github.io/images/article/2026-6-27-NebulaPulsar/9.png" width=700/>
</p>

---

### ASMX

<p align="center">
    <img src="https://iss4cf0ng.github.io/images/article/2026-6-27-NebulaPulsar/13.png" width=700/>
</p>

---

### ASHX

<p align="center">
    <img src="https://iss4cf0ng.github.io/images/article/2026-6-27-NebulaPulsar/14.png" width=700/>
</p>

---

### Wireshark

<p align="center">
    <img src="https://iss4cf0ng.github.io/images/article/2026-6-27-NebulaPulsar/8.png" width=700/>
</p>

---

## Usage

```bash
python3 main.py --url http://localhost/nebulapulsar.jsp --script java --encoding='utf-8'
```
