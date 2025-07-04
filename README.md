# 💳 Pix por Aproximação (NFC) – Proposta de Integração

Este repositório contém uma proposta técnica e uma prova de conceito para implementação do **Pix por Aproximação**, utilizando **React Native** com módulo nativo em **Kotlin (Android)** e comunicação via **APDU** com dispositivos que implementam **HCE (Host Card Emulation)**.

## 📄 Conteúdo

- Documento técnico: `Proposta de Integração - Pix por Aproximação.pdf`
- Documento técnico do Banco Central: `Especificações do Pix por aproximação para Android.pdf`
- Códigos Kotlin e JavaScript
- Exemplos de uso dos comandos APDU `SELECT AID` e `UPDATE BINARY`
- Explicações sobre construção da URI Pix e limitações de uso

## ⚠️ Aviso

Este repositório **não contém a implementação completa** do terminal NFC. Ele apresenta apenas as **classes essenciais** e os **blocos de código principais** 

## 📱 Tecnologias utilizadas

- [React Native](https://reactnative.dev/)
- Android NFC (`IsoDep`, `NfcAdapter`)
- Kotlin (módulo nativo)
- APDU (Application Protocol Data Unit)
- NDEF (NFC Data Exchange Format)