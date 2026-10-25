import type { Metadata } from "next";

import { Bullets, Clause, PolicyDocument, PolicyTable } from "@/components/PolicyDocument";
import { COMPANY, POLICY_EFFECTIVE_DATE } from "@/lib/company";

export const metadata: Metadata = { title: "배송·교환·환불 안내" };

// 주문·클레임 처리 규칙(OrderService, ClaimService, 자동 구매확정)과 같아야 한다
export default function PolicyPage() {
  return (
    <PolicyDocument title="배송·교환·환불 안내" current="/policy" effectiveDate={POLICY_EFFECTIVE_DATE}>
      <p>
        {COMPANY.shopName}은 재고·반품·리퍼·중고 상품을 검수하여 판매합니다. 상품마다 <strong>상태등급과 상태 설명, 실물
        사진</strong>을 표시하니 구매 전에 꼭 확인해 주세요.
      </p>

      <Clause title="결제">
        <PolicyTable
          head={["결제 수단", "안내"]}
          rows={[
            [
              "무통장입금",
              "주문 후 안내되는 회사 계좌로 입금해 주세요. 입금 기한(주문 후 48시간) 안에 입금되지 않으면 주문이 자동으로 취소됩니다. 입금자명과 금액이 주문과 같아야 확인이 빠릅니다.",
            ],
            ["신용카드·간편결제", "결제창에서 결제를 마치면 바로 결제완료됩니다. 결제창을 띄운 뒤 30분 안에 결제하지 않으면 주문이 취소됩니다."],
          ]}
        />
        {COMPANY.escrow && <p>고객님의 안전거래를 위해 현금 결제 시 {COMPANY.escrow} 구매안전서비스를 이용하실 수 있습니다.</p>}
      </Clause>

      <Clause title="배송">
        <Bullets>
          <li>배송 지역: 전국 (제주·도서산간 지역은 추가 배송비가 생길 수 있으며, 주문 전 고객센터로 문의해 주세요)</li>
          <li>배송비: 상품마다 표시된 금액이며, 같은 판매자 상품은 한 번만 부과합니다. 상품에 무료배송 기준 금액이 있으면 그 이상 구매 시 무료입니다.</li>
          <li>출고: 결제(무통장입금은 입금 확인) 후 영업일 기준 1~3일 안에 출고합니다. 재고 확인이 필요한 상품은 늦어질 수 있으며 이 경우 따로 연락드립니다.</li>
          <li>배송 기간: 출고 후 보통 1~2일 (택배사 사정에 따라 달라질 수 있습니다)</li>
          <li>송장번호는 주문 상세에서 확인할 수 있습니다.</li>
        </Bullets>
      </Clause>

      <Clause title="주문 취소">
        <Bullets>
          <li>입금 전(무통장입금) 주문은 주문 상세에서 바로 취소할 수 있습니다.</li>
          <li>결제완료 상태(상품 준비 전)에서는 주문 상세에서 직접 취소할 수 있습니다. 환불은 아래 &lsquo;환불&rsquo;을 참고해 주세요.</li>
          <li>상품 준비가 시작되었거나 발송된 뒤에는 취소할 수 없고, 받으신 뒤 반품으로 신청해 주세요.</li>
        </Bullets>
      </Clause>

      <Clause title="반품">
        <Bullets>
          <li>신청 기간: 상품을 받은 날부터 7일 이내, 주문 상세에서 신청합니다. 상품이 표시·광고 내용과 다른 경우 받은 날부터 3개월, 그 사실을 안 날부터 30일 이내에 신청할 수 있습니다.</li>
          <li>배송완료 8일 뒤 자동으로 구매확정되며, 구매확정 후에는 온라인 반품 신청이 닫힙니다. 이후 하자는 고객센터로 문의해 주세요.</li>
          <li>반품지와 반품 배송비는 상품 상세의 &lsquo;반품·교환 안내&rsquo;에 표시됩니다.</li>
        </Bullets>
        <PolicyTable
          head={["반품 사유", "반품 배송비"]}
          rows={[
            ["단순변심, 상품 상세에 미리 표시된 상태(사용감·흠집·구성품 누락 등)", "고객 부담 (무료배송으로 받은 주문은 왕복 배송비) — 환불 금액에서 차감"],
            ["상품 불량, 오배송, 표시와 다른 상품", "판매자 부담 (전액 환불)"],
          ]}
        />
        <p className="font-semibold text-ink">다음의 경우에는 반품할 수 없습니다.</p>
        <Bullets>
          <li>고객님의 책임으로 상품이 멸실·훼손된 경우 (내용 확인을 위한 포장 개봉은 제외)</li>
          <li>사용하거나 일부 소비하여 상품 가치가 현저히 줄어든 경우</li>
          <li>시간이 지나 다시 판매하기 어려울 정도로 상품 가치가 줄어든 경우</li>
          <li>복제가 가능한 상품의 포장을 훼손한 경우</li>
        </Bullets>
      </Clause>

      <Clause title="교환">
        <p>
          재고·리퍼 상품은 대부분 같은 상품이 1개뿐이어서 교환 대신 <strong>반품 후 다시 주문</strong>하는 방식으로 처리합니다.
          같은 상품의 재고가 남아 있는 경우에는 고객센터로 문의해 주시면 교환을 도와드립니다.
        </p>
      </Clause>

      <Clause title="환불">
        <PolicyTable
          head={["결제 수단", "환불 방법", "환불 시기"]}
          rows={[
            ["신용카드·간편결제", "결제 취소 (카드사 승인 취소)", "취소 즉시 요청, 카드사에 따라 3~7영업일 내 반영"],
            ["무통장입금", "고객님이 알려 주신 계좌로 송금 (담당자가 연락드려 환불 계좌를 확인합니다)", "반품 상품 도착·검수 후(취소는 취소 후) 3영업일 이내"],
          ]}
        />
      </Clause>

      <Clause title="고객센터">
        <p>
          {COMPANY.phone} · {COMPANY.csHours}
          {COMPANY.email && ` · ${COMPANY.email}`}
        </p>
      </Clause>
    </PolicyDocument>
  );
}
