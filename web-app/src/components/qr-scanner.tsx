"use client";

import { useEffect, useRef, useState } from "react";
import { Html5QrcodeScanner } from "html5-qrcode";

export default function QRScanner({ onScan, onError }: { onScan: (data: string) => void, onError?: (err: any) => void }) {
  const scannerRef = useRef<Html5QrcodeScanner | null>(null);

  useEffect(() => {
    scannerRef.current = new Html5QrcodeScanner(
      "qr-reader",
      { fps: 10, qrbox: { width: 250, height: 250 } },
      /* verbose= */ false
    );
    
    scannerRef.current.render(
      (decodedText) => {
        onScan(decodedText);
        scannerRef.current?.clear();
      },
      (error) => {
        if (onError) onError(error);
      }
    );

    return () => {
      scannerRef.current?.clear().catch(console.error);
    };
  }, [onScan, onError]);

  return <div id="qr-reader" className="w-full max-w-sm mx-auto overflow-hidden rounded-lg bg-white" />;
}
