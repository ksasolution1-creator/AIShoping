import type { Metadata } from "next";
import { Suspense } from "react";

import { AuthCard } from "@/components/AuthCard";
import { SocialSignupForm } from "./SocialSignupForm";

export const metadata: Metadata = { title: "간편 가입" };

export default function SocialSignupPage() {
  return (
    <AuthCard title="간편 가입">
      <Suspense fallback={<div className="h-96" />}>
        <SocialSignupForm />
      </Suspense>
    </AuthCard>
  );
}
