import logging

from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from services.llm_service import llm_service
from agents.tools.account_tool import account_tool
from agents.tools.transaction_tool import transaction_tool
from agents.tools.payment_tool import payment_tool
from agents.tools.rbi_knowledge_tool import rbi_knowledge_tool

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You are PayStream AI Assistant — an intelligent banking agent for PayStream
Banking Platform. You help customers with:
- Checking account balances and transaction history
- Tracking payment status (NEFT/RTGS/IMPS/UPI)
- Answering questions about RBI payment rules and limits
- Explaining payment rejections or delays

You have access to tools to query real-time account and payment data.
Always be accurate, professional, and cite RBI guidelines when relevant.
Never initiate transactions without explicit customer confirmation.
Always protect customer privacy — never expose full account numbers.

Current Customer: {customer_id}
Account: {account_number}

Conversation history so far (may be empty):
{conversation_history}

Call a tool at most once per distinct piece of information you need. As soon
as a tool's result answers the customer's question, respond directly in
plain language — do not call the same tool again with the same or a
rephrased input.
"""

TOOLS = [account_tool, transaction_tool, payment_tool, rbi_knowledge_tool]


def build_agent_executor() -> AgentExecutor:
    # ChatGroq is a chat/instruction-tuned model, not a text-completion model like
    # the previous OllamaLLM — the old text-based ReAct agent (create_react_agent)
    # doesn't reliably terminate with chat models (it re-issues the same Action
    # instead of producing a Final Answer). Groq's tool-calling API is native for
    # chat models, so create_tool_calling_agent is used instead of text parsing.
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", SYSTEM_PROMPT),
            ("human", "{input}"),
            MessagesPlaceholder("agent_scratchpad"),
        ]
    )
    agent = create_tool_calling_agent(llm=llm_service.llm, tools=TOOLS, prompt=prompt)
    return AgentExecutor(
        agent=agent,
        tools=TOOLS,
        verbose=False,
        handle_parsing_errors=True,
        max_iterations=6,
        return_intermediate_steps=True,
    )


banking_agent_executor = build_agent_executor()
