import type { Metadata } from "next";
import { Suspense } from "react";

import { AuthCard } from "@/components/AuthCard";
import { SignupForm } from "./SignupForm";

export const metadata: Metadata = { title: "회원가입" };

export default function SignupPage() {
  return (
    <AuthCard title="회원가입">
      <Suspense fallback={<div className="h-96" />}>
        <SignupForm />
      </Suspense>
    </AuthCard>
  );
}
