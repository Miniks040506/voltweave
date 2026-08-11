export default function Home() {
  return (
    <main className="flex min-h-screen items-center bg-[#08110e] px-6 py-20 text-[#eef7f0]">
      <section className="mx-auto w-full max-w-5xl">
        <p className="mb-8 font-mono text-xs tracking-[0.3em] text-emerald-300">
          VOLTWEAVE / V1
        </p>
        <h1 className="max-w-3xl text-5xl font-semibold tracking-[-0.04em] sm:text-7xl">
          Orchestrate distributed energy.
        </h1>
        <p className="mt-8 max-w-2xl text-lg leading-8 text-[#9eb3a5]">
          A virtual power plant sandbox for forecasting, dispatching and
          settling flexible household energy.
        </p>
        <div className="mt-14 inline-flex items-center gap-3 border border-emerald-300/20 bg-emerald-300/5 px-4 py-3 font-mono text-sm text-emerald-200">
          <span
            className="h-2 w-2 rounded-full bg-emerald-300"
            aria-hidden="true"
          />
          Project foundation ready
        </div>
      </section>
    </main>
  );
}
