// 사업자 정보 (하단, 이용약관, 개인정보처리방침, 이용안내가 함께 쓴다). 바뀌면 여기만 고친다.
export const COMPANY = {
  shopName: "떨고래 마켓",
  name: "(주)케이에스에이솔루션",
  ceo: "김민혁",
  businessNo: "751-88-00289",
  mailOrderNo: "2025-서울금천-1886",
  address: "서울특별시 금천구 벚꽃로 286, 삼성리더스타워 12층 1211호 (가산동)",
  phone: "010-5275-3772",
  /** 고객센터 이메일. 비어 있으면 표시하지 않는다 */
  email: "kmh@ksas.co.kr",
  /** 고객센터 운영 시간 */
  csHours: "평일 10:00~18:00 (주말·공휴일 휴무)",
  privacyOfficer: "김민혁",
  hostingProvider: "Vultr",
  /** 구매안전서비스(결제대금예치·소비자피해보상보험) 가입 정보. 예: "기업은행 에스크로" */
  escrow: "",
  /** 사업자정보 확인 (공정거래위원회) */
  ftcUrl: "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=7518800289",
} as const;

/** 약관·방침 시행일 */
export const POLICY_EFFECTIVE_DATE = "2026년 10월 25일";
